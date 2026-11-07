package org.example.vacancyparser.hh;

import org.example.vacancyparser.util.TraceContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

@Component
public class VirtualThreadDetailLoader implements VacancyDetailLoader {

    private final VacancyDetailFetcher fetcher;

    public VirtualThreadDetailLoader(VacancyDetailFetcher fetcher) {
        this.fetcher = fetcher;
    }

    @Override
    public String mode() {
        return "virtual";
    }

    @Override
    public Map<String, HhVacancyDetails> load(List<HhRssItem> items,
                                              int concurrency,
                                              long delayMs,
                                              BiConsumer<String, Throwable> onFailure) {
        Map<String, HhVacancyDetails> details = new ConcurrentHashMap<>();
        Semaphore limiter = new Semaphore(Math.max(1, concurrency));

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>(items.size());
            for (HhRssItem item : items) {
                futures.add(executor.submit(TraceContext.wrap(
                        () -> fetchOne(item, delayMs, limiter, details, onFailure))));
            }

            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    onFailure.accept("virtual", e);
                    break;
                } catch (ExecutionException e) {
                    onFailure.accept("virtual", e.getCause() == null ? e : e.getCause());
                }
            }
        }

        return details;
    }

    private void fetchOne(HhRssItem item,
                          long delayMs,
                          Semaphore limiter,
                          Map<String, HhVacancyDetails> details,
                          BiConsumer<String, Throwable> onFailure) {
        boolean acquired = false;
        try {
            limiter.acquire();
            acquired = true;
            HhVacancyDetails parsed = fetcher.fetch(item, delayMs);
            if (parsed != null) {
                details.put(item.externalId(), parsed);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onFailure.accept(item.externalId(), e);
        } catch (RuntimeException e) {
            onFailure.accept(item.externalId(), e);
        } finally {
            if (acquired) {
                limiter.release();
            }
        }
    }
}
