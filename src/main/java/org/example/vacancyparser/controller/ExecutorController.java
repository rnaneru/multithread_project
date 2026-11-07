package org.example.vacancyparser.controller;

import org.example.vacancyparser.dto.ExecutorStats;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("/api/executor")
public class ExecutorController {

    private final ThreadPoolExecutor executor;

    public ExecutorController(@Qualifier("hhParserExecutor") ThreadPoolExecutor executor) {
        this.executor = executor;
    }

    @GetMapping("/stats")
    public ExecutorStats stats() {
        return new ExecutorStats(
                executor.getCorePoolSize(),
                executor.getMaximumPoolSize(),
                executor.getPoolSize(),
                executor.getActiveCount(),
                executor.getQueue().size(),
                executor.getTaskCount(),
                executor.getCompletedTaskCount()
        );
    }
}
