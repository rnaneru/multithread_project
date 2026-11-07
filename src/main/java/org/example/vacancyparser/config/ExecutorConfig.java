package org.example.vacancyparser.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class ExecutorConfig {

    @Bean(name = "hhParserExecutor", destroyMethod = "shutdown")
    public ThreadPoolExecutor hhParserExecutor(ParserProperties properties) {
        AtomicInteger threadCounter = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "hh-parser-" + threadCounter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                properties.executorCoreSize(),
                properties.executorMaxSize(),
                60L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(properties.executorQueueCapacity()),
                threadFactory,
                new ThreadPoolExecutor.CallerRunsPolicy()
        );
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    @Bean(name = "parseOrchestrator", destroyMethod = "shutdown")
    public ExecutorService parseOrchestrator() {
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "parse-orchestrator");
            thread.setDaemon(true);
            return thread;
        });
    }

    @Bean(name = "parseScheduler", destroyMethod = "shutdown")
    public ScheduledExecutorService parseScheduler() {
        AtomicInteger counter = new AtomicInteger(1);
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "parse-scheduler-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        });
    }
}
