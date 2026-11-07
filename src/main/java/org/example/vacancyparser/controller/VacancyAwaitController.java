package org.example.vacancyparser.controller;

import lombok.RequiredArgsConstructor;
import org.example.vacancyparser.service.VacancyIngestionMonitor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vacancies")
@RequiredArgsConstructor
public class VacancyAwaitController {

    private final VacancyIngestionMonitor ingestionMonitor;

    @GetMapping("/await")
    public VacancyIngestionMonitor.IngestionEvent await(
            @RequestParam(defaultValue = "0") long version,
            @RequestParam(defaultValue = "30000") long timeoutMs) throws InterruptedException {

        long safeTimeout = Math.max(1, Math.min(timeoutMs, 120_000));
        return ingestionMonitor.awaitAfter(version, safeTimeout)
                .orElseGet(ingestionMonitor::timeoutEvent);
    }
}
