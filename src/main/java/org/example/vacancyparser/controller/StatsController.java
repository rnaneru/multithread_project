package org.example.vacancyparser.controller;

import lombok.RequiredArgsConstructor;
import org.example.vacancyparser.dto.StatsResponse;
import org.example.vacancyparser.service.SalaryStatisticsService;
import org.example.vacancyparser.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;
    private final SalaryStatisticsService salaryStatisticsService;

    @GetMapping
    public StatsResponse stats() {
        return statsService.getStats();
    }

    @GetMapping("/salary-forkjoin")
    public SalaryStatisticsService.SalaryStatistics salaryForkJoin() {
        return salaryStatisticsService.compute();
    }
}
