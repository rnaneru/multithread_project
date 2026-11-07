package org.example.vacancyparser.hh;

import org.example.vacancyparser.util.TraceContext;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.BiConsumer;

@Component
public class PlatformDetailLoader implements VacancyDetailLoader {

    private final VacancyDetailFetcher fetcher;
    private final ThreadPoolExecutor executor;

    public PlatformDetailLoader(VacancyDetailFetcher fetcher,
                                @Qualifier("hhParserExecutor") ThreadPoolExecutor executor) {
        this.fetcher = fetcher;
        this.executor = executor;
    }

    @Override
    public String mode() {
        return "platform";
    }

    @Override
    public Map<String, HhVacancyDetails> load(List<HhRssItem> items,
                                              int concurrency,
                                              long delayMs,
                                              BiConsumer<String, Throwable> onFailure) {
        Map<String, HhVacancyDetails> details = new ConcurrentHashMap<>();
        Semaphore limiter = new Semaphore(Math.max(1, concurrency));

        List<CompletableFuture<Void>> futures = items.stream()
                .map(item -> CompletableFuture.runAsync(
                        TraceContext.wrap(() -> fetchOne(item, delayMs, limiter, details, onFailure)),
                        executor))
                .toList();

        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
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
