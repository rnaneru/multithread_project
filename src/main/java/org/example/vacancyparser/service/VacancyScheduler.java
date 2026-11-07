package org.example.vacancyparser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.vacancyparser.config.ParserProperties;
import org.example.vacancyparser.dto.ParseJobView;
import org.example.vacancyparser.dto.ParseRequest;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VacancyScheduler {

    private final ParseJobService parseJobService;
    private final ParserProperties properties;

    @EventListener(ApplicationReadyEvent.class)
    public void parseOnStartup() {
        if (!properties.startupEnabled()) {
            log.info("Стартовый парсинг отключён (app.parser.startup-enabled=false)");
            return;
        }
        ParseJobView job = parseJobService.submit(new ParseRequest(
                properties.defaultQuery(),
                properties.defaultArea(),
                properties.startupPages(),
                true
        ));
        log.info("Стартовый парсинг запущен: jobId={}, query='{}', pages={}",
                job.jobId(), job.query(), job.requestedPages());
    }

    @Scheduled(
            initialDelayString = "${app.parser.schedule-initial-delay-ms:600000}",
            fixedDelayString = "${app.parser.schedule-delay-ms:1800000}"
    )
    public void parseBySchedule() {
        if (!properties.scheduleEnabled()) {
            return;
        }
        ParseJobView job = parseJobService.submit(new ParseRequest(
                properties.defaultQuery(),
                properties.defaultArea(),
                properties.schedulePages(),
                true
        ));
        log.info("Плановый парсинг запущен: jobId={}, query='{}', pages={}",
                job.jobId(), job.query(), job.requestedPages());
    }
}
