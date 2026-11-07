package org.example.vacancyparser.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.parser")
public record ParserProperties(
        String rssBaseUrl,
        String vacancyBaseUrl,
        String userAgent,
        String defaultQuery,
        int defaultArea,
        int defaultPages,
        int maxPages,
        int maxDetails,
        int detailConcurrency,
        long detailDelayMs,
        String detailMode,
        int requestTimeoutMs,
        int maxAttempts,
        int executorCoreSize,
        int executorMaxSize,
        int executorQueueCapacity,
        int startupPages,
        int schedulePages,
        boolean startupEnabled,
        boolean scheduleEnabled,
        long syncTimeoutMs
) {
}
