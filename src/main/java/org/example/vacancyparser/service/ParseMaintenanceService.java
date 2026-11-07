package org.example.vacancyparser.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class ParseMaintenanceService {

    private final ScheduledExecutorService scheduler;
    private final ParseJobService parseJobService;

    public ParseMaintenanceService(@Qualifier("parseScheduler") ScheduledExecutorService scheduler,
                                   ParseJobService parseJobService) {
        this.scheduler = scheduler;
        this.parseJobService = parseJobService;
    }

    @PostConstruct
    void start() {
        scheduler.scheduleAtFixedRate(this::runMaintenance, 5, 5, TimeUnit.MINUTES);
        log.info("Сервис обслуживания запущен: очистка истории задач каждые 5 минут");
    }

    private void runMaintenance() {
        try {
            parseJobService.evictOldJobs();
            log.info("Обслуживание выполнено, задач в истории: {}", parseJobService.findAll().size());
        } catch (Exception e) {
            log.error("Ошибка фонового обслуживания", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
