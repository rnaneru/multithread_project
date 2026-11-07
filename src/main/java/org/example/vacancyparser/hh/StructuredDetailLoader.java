package org.example.vacancyparser.hh;

import lombok.extern.slf4j.Slf4j;
import org.example.vacancyparser.util.TraceContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

@Component
@Slf4j
public class StructuredDetailLoader implements VacancyDetailLoader {

    private final VacancyDetailFetcher fetcher;
    private final VacancyDetailLoader fallback;
    private final ScopeApi scopeApi;

    public StructuredDetailLoader(VacancyDetailFetcher fetcher, VirtualThreadDetailLoader fallback) {
        this.fetcher = fetcher;
        this.fallback = fallback;
        this.scopeApi = ScopeApi.resolve();
        if (scopeApi == null) {
            log.warn("StructuredTaskScope недоступен в этой JDK: режим structured будет работать через virtual");
        }
    }

    @Override
    public String mode() {
        return "structured";
    }

    @Override
    public Map<String, HhVacancyDetails> load(List<HhRssItem> items,
                                              int concurrency,
                                              long delayMs,
                                              BiConsumer<String, Throwable> onFailure) {
        if (scopeApi == null) {
            return fallback.load(items, concurrency, delayMs, onFailure);
        }

        Map<String, HhVacancyDetails> details = new ConcurrentHashMap<>();
        Semaphore limiter = new Semaphore(Math.max(1, concurrency));

        Object scope = null;
        try {
            scope = scopeApi.create();
            for (HhRssItem item : items) {
                scopeApi.fork(scope, TraceContext.wrapCallable(() -> {
                    fetchOne(item, delayMs, limiter, details, onFailure);
                    return null;
                }));
            }
            scopeApi.join(scope);
            scopeApi.throwIfFailed(scope);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onFailure.accept("structured", e);
        } catch (Throwable e) {
            onFailure.accept("structured", e);
        } finally {
            scopeApi.closeQuietly(scope);
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

    private static final class ScopeApi {

        private final Class<?> shutdownType;
        private final Method fork;
        private final Method join;
        private final Method throwIfFailed;
        private final Method close;

        private ScopeApi(Class<?> shutdownType,
                         Method fork,
                         Method join,
                         Method throwIfFailed,
                         Method close) {
            this.shutdownType = shutdownType;
            this.fork = fork;
            this.join = join;
            this.throwIfFailed = throwIfFailed;
            this.close = close;
        }

        static ScopeApi resolve() {
            try {
                Class<?> scopeType = Class.forName("java.util.concurrent.StructuredTaskScope");
                Class<?> shutdownType = Class.forName("java.util.concurrent.StructuredTaskScope$ShutdownOnFailure");
                return new ScopeApi(
                        shutdownType,
                        scopeType.getMethod("fork", Callable.class),
                        scopeType.getMethod("join"),
                        shutdownType.getMethod("throwIfFailed"),
                        scopeType.getMethod("close")
                );
            } catch (ReflectiveOperationException | RuntimeException e) {
                return null;
            }
        }

        Object create() throws ReflectiveOperationException {
            return shutdownType.getConstructor().newInstance();
        }

        void fork(Object scope, Callable<?> task) throws Throwable {
            invoke(fork, scope, task);
        }

        void join(Object scope) throws Throwable {
            invoke(join, scope);
        }

        void throwIfFailed(Object scope) throws Throwable {
            invoke(throwIfFailed, scope);
        }

        void closeQuietly(Object scope) {
            if (scope == null) {
                return;
            }
            try {
                invoke(close, scope);
            } catch (Throwable e) {
                log.debug("Не удалось закрыть StructuredTaskScope: {}", e.toString());
            }
        }

        private static Object invoke(Method method, Object target, Object... args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause() == null ? e : e.getCause();
            }
        }
    }
}
