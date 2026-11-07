package org.example.vacancyparser.dto;

public record ExecutorStats(
        int corePoolSize,
        int maxPoolSize,
        int poolSize,
        int activeThreads,
        int queuedTasks,
        long submittedTasks,
        long completedTasks
) {
}
