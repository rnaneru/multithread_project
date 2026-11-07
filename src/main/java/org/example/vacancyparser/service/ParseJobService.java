package org.example.vacancyparser.service;

import lombok.extern.slf4j.Slf4j;
import org.example.vacancyparser.config.ParserProperties;
import org.example.vacancyparser.dto.ParseJobView;
import org.example.vacancyparser.dto.ParseRequest;
import org.example.vacancyparser.dto.SaveResult;
import org.example.vacancyparser.hh.HhClient;
import org.example.vacancyparser.hh.HhRssItem;
import org.example.vacancyparser.hh.HhRssParser;
import org.example.vacancyparser.hh.HhVacancyDetails;
import org.example.vacancyparser.hh.ParsedVacancy;
import org.example.vacancyparser.hh.VacancyDetailLoader;
import org.example.vacancyparser.util.TraceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class ParseJobService {

    private static final int MAX_JOBS = 50;
    private static final int MAX_WARNINGS = 50;

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";

    private final HhClient hhClient;
    private final HhRssParser rssParser;
    private final VacancyIngestionService ingestionService;
    private final ParserProperties properties;
    private final ThreadPoolExecutor hhParserExecutor;
    private final ExecutorService parseOrchestrator;
    private final ParseTaskQueue taskQueue;
    private final List<VacancyDetailLoader> detailLoaders;

    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> jobOrder = new ConcurrentLinkedDeque<>();

    public ParseJobService(HhClient hhClient,
                           HhRssParser rssParser,
                           VacancyIngestionService ingestionService,
                           ParserProperties properties,
                           @Qualifier("hhParserExecutor") ThreadPoolExecutor hhParserExecutor,
                           @Qualifier("parseOrchestrator") ExecutorService parseOrchestrator,
                           ParseTaskQueue taskQueue,
                           List<VacancyDetailLoader> detailLoaders) {
        this.hhClient = hhClient;
        this.rssParser = rssParser;
        this.ingestionService = ingestionService;
        this.properties = properties;
        this.hhParserExecutor = hhParserExecutor;
        this.parseOrchestrator = parseOrchestrator;
        this.taskQueue = taskQueue;
        this.detailLoaders = detailLoaders;
    }

    @PostConstruct
    void startConsumer() {
        parseOrchestrator.execute(this::consumeLoop);
    }

    private void consumeLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Optional<Runnable> task = taskQueue.take();
                if (task.isEmpty()) {
                    return;
                }
                task.get().run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.error("Ошибка выполнения задачи парсинга", e);
            }
        }
    }

    public ParseJobView submit(ParseRequest request) {
        NormalizedRequest normalized = normalize(request);
        JobState state = new JobState(
                UUID.randomUUID().toString(),
                normalized.query(),
                normalized.area(),
                normalized.pages(),
                normalized.enrichDetails()
        );

        jobs.put(state.jobId, state);
        jobOrder.addLast(state.jobId);
        evictOldJobs();

        try {
            taskQueue.put(() -> runJob(state));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            state.status = STATUS_FAILED;
            state.error = "Постановка задачи в очередь прервана";
            state.finish();
            throw new IllegalStateException("Не удалось запустить задачу парсинга", e);
        } catch (RejectedExecutionException e) {
            state.status = STATUS_FAILED;
            state.error = "Очередь задач переполнена";
            state.finish();
            throw new IllegalStateException("Не удалось запустить задачу парсинга", e);
        }

        log.info("Задача {} поставлена в очередь: query='{}', area={}, pages={}, details={}",
                state.jobId, state.query, state.area, state.requestedPages, state.enrichDetails);
        return state.toView();
    }

    public Optional<ParseJobView> find(String jobId) {
        JobState state = jobs.get(jobId);
        return state == null ? Optional.empty() : Optional.of(state.toView());
    }

    public List<ParseJobView> findAll() {
        List<ParseJobView> result = new ArrayList<>();
        Iterator<String> iterator = jobOrder.descendingIterator();
        while (iterator.hasNext()) {
            JobState state = jobs.get(iterator.next());
            if (state != null) {
                result.add(state.toView());
            }
        }
        return result;
    }

    public Optional<ParseJobView> waitFor(String jobId, long timeoutMs) {
        JobState state = jobs.get(jobId);
        if (state == null) {
            return Optional.empty();
        }
        try {
            state.done.get(Math.max(1, timeoutMs), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            log.info("Задача {} ещё выполняется, возвращаем промежуточное состояние", jobId);
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
        return Optional.of(state.toView());
    }

    public boolean isRunning(String jobId) {
        JobState state = jobs.get(jobId);
        return state != null && STATUS_RUNNING.equals(state.status);
    }

    private void runJob(JobState state) {
        String traceId = "job-" + state.jobId.substring(0, 8);
        TraceContext.set(traceId);
        state.status = STATUS_RUNNING;
        try {
            Map<String, HhRssItem> uniqueItems = new ConcurrentHashMap<>();
            Queue<String> order = new ConcurrentLinkedQueue<>();

            fetchRssPages(state, uniqueItems, order);

            state.uniqueItems.set(order.size());
            if (order.isEmpty()) {
                state.addWarning("hh.ru не вернул ни одной вакансии — проверьте интернет или поисковый запрос");
                state.status = STATUS_COMPLETED;
                return;
            }

            Map<String, HhVacancyDetails> details = state.enrichDetails
                    ? enrichWithDetails(state, uniqueItems, order)
                    : Collections.emptyMap();

            List<ParsedVacancy> merged = new ArrayList<>(order.size());
            for (String externalId : order) {
                HhRssItem item = uniqueItems.get(externalId);
                HhVacancyDetails detail = details.get(externalId);
                merged.add(ParsedVacancy.fromRss(item).withDetails(detail));
            }

            SaveResult saveResult = ingestionService.saveAll(merged);
            state.created.set(saveResult.created());
            state.updated.set(saveResult.updated());
            state.status = STATUS_COMPLETED;

            log.info("Задача {} завершена: RSS={}, уникальных={}, деталей={}, ошибок деталей={}, новых={}, обновлено={}",
                    state.jobId, state.rssItems.get(), state.uniqueItems.get(), state.detailsFetched.get(),
                    state.detailsFailed.get(), state.created.get(), state.updated.get());
        } catch (Exception e) {
            state.status = STATUS_FAILED;
            state.error = rootMessage(e);
            log.error("Задача {} завершилась ошибкой", state.jobId, e);
        } finally {
            TraceContext.clear();
            state.finish();
        }
    }

    private void fetchRssPages(JobState state,
                               Map<String, HhRssItem> uniqueItems,
                               Queue<String> order) {
        List<CompletableFuture<PageResult>> futures = new ArrayList<>(state.requestedPages);
        for (int page = 0; page < state.requestedPages; page++) {
            final int pageNumber = page;
            futures.add(CompletableFuture.supplyAsync(TraceContext.wrap(() -> {
                String xml = hhClient.fetchRssPage(state.query, state.area, pageNumber);
                return new PageResult(pageNumber, rssParser.parse(xml));
            }), hhParserExecutor));
        }

        for (CompletableFuture<PageResult> future : futures) {
            try {
                PageResult page = future.join();
                state.pagesFetched.incrementAndGet();
                state.rssItems.addAndGet(page.items().size());
                for (HhRssItem item : page.items()) {
                    if (uniqueItems.putIfAbsent(item.externalId(), item) == null) {
                        order.add(item.externalId());
                    }
                }
            } catch (CompletionException e) {
                state.addWarning("Не удалось загрузить страницу RSS: " + rootMessage(e));
            }
        }
    }

    private Map<String, HhVacancyDetails> enrichWithDetails(JobState state,
                                                            Map<String, HhRssItem> uniqueItems,
                                                            Queue<String> order) {
        int limit = Math.max(0, Math.min(properties.maxDetails(), order.size()));
        List<HhRssItem> items = order.stream()
                .limit(limit)
                .map(uniqueItems::get)
                .filter(Objects::nonNull)
                .toList();

        VacancyDetailLoader loader = selectDetailLoader();
        Map<String, HhVacancyDetails> details = loader.load(
                items,
                properties.detailConcurrency(),
                properties.detailDelayMs(),
                (externalId, error) -> {
                    state.detailsFailed.incrementAndGet();
                    state.addWarning("Вакансия " + externalId + ": " + rootMessage(error));
                });

        state.detailsFetched.addAndGet(details.size());
        log.debug("Детали загружены режимом {}: {} из {}", loader.mode(), details.size(), items.size());
        return details;
    }

    private VacancyDetailLoader selectDetailLoader() {
        String requested = properties.detailMode() == null ? "platform" : properties.detailMode().trim();
        return detailLoaders.stream()
                .filter(loader -> loader.mode().equalsIgnoreCase(requested))
                .findFirst()
                .orElseGet(() -> {
                    log.warn("Режим загрузки деталей '{}' не найден, используется platform", requested);
                    return detailLoaders.stream()
                            .filter(loader -> "platform".equals(loader.mode()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException("Не найден загрузчик деталей platform"));
                });
    }

    private NormalizedRequest normalize(ParseRequest request) {
        String query = request == null || request.query() == null || request.query().isBlank()
                ? properties.defaultQuery()
                : request.query().trim();

        int area = request == null || request.area() == null
                ? properties.defaultArea()
                : request.area();

        int pages = request == null || request.pages() == null
                ? properties.defaultPages()
                : request.pages();
        pages = Math.max(1, Math.min(pages, Math.max(1, properties.maxPages())));

        boolean enrichDetails = request == null || request.enrichDetails() == null
                || request.enrichDetails();

        return new NormalizedRequest(query, area, pages, enrichDetails);
    }

    public synchronized void evictOldJobs() {
        while (jobs.size() > MAX_JOBS) {
            String oldest = jobOrder.pollFirst();
            if (oldest == null) {
                return;
            }
            JobState state = jobs.get(oldest);
            if (state != null && STATUS_RUNNING.equals(state.status)) {
                jobOrder.addLast(oldest);
                return;
            }
            jobs.remove(oldest);
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private record PageResult(int page, List<HhRssItem> items) {
    }

    private record NormalizedRequest(String query, int area, int pages, boolean enrichDetails) {
    }

    private static final class JobState {

        private final String jobId;
        private final String query;
        private final int area;
        private final int requestedPages;
        private final boolean enrichDetails;
        private final Instant startedAt = Instant.now();
        private final AtomicInteger pagesFetched = new AtomicInteger();
        private final AtomicInteger rssItems = new AtomicInteger();
        private final AtomicInteger uniqueItems = new AtomicInteger();
        private final AtomicInteger detailsFetched = new AtomicInteger();
        private final AtomicInteger detailsFailed = new AtomicInteger();
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger updated = new AtomicInteger();
        private final Queue<String> warnings = new ConcurrentLinkedQueue<>();
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        private volatile String status = STATUS_PENDING;
        private volatile Instant finishedAt;
        private volatile String error;

        private JobState(String jobId, String query, int area, int requestedPages, boolean enrichDetails) {
            this.jobId = jobId;
            this.query = query;
            this.area = area;
            this.requestedPages = requestedPages;
            this.enrichDetails = enrichDetails;
        }

        private void addWarning(String warning) {
            if (warnings.size() < MAX_WARNINGS) {
                warnings.add(warning);
            }
        }

        private void finish() {
            if (finishedAt == null) {
                finishedAt = Instant.now();
            }
            done.complete(null);
        }

        private ParseJobView toView() {
            Instant end = finishedAt != null ? finishedAt : Instant.now();
            return new ParseJobView(
                    jobId,
                    query,
                    area,
                    requestedPages,
                    enrichDetails,
                    status,
                    startedAt,
                    finishedAt,
                    Duration.between(startedAt, end).toMillis(),
                    pagesFetched.get(),
                    rssItems.get(),
                    uniqueItems.get(),
                    detailsFetched.get(),
                    detailsFailed.get(),
                    created.get(),
                    updated.get(),
                    List.copyOf(warnings),
                    error
            );
        }
    }
}
