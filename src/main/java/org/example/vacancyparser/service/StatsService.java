package org.example.vacancyparser.service;

import lombok.RequiredArgsConstructor;
import org.example.vacancyparser.domain.Vacancy;
import org.example.vacancyparser.dto.StatsResponse;
import org.example.vacancyparser.repository.VacancyRepository;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Comparator;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private static final int TOP_LIMIT = 10;

    private final VacancyRepository repository;

    private final ReentrantReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private volatile StatsResponse cachedStats;

    public StatsResponse getStats() {
        StatsResponse snapshot = cachedStats;
        if (snapshot != null) {
            return snapshot;
        }

        cacheLock.readLock().lock();
        try {
            if (cachedStats != null) {
                return cachedStats;
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        StatsResponse computed = computeStats();

        cacheLock.writeLock().lock();
        try {
            if (cachedStats == null) {
                cachedStats = computed;
            }
            return cachedStats;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    public void invalidate() {
        cacheLock.writeLock().lock();
        try {
            cachedStats = null;
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private StatsResponse computeStats() {
        List<Vacancy> all = repository.findAll();

        Map<String, Long> byCity = all.parallelStream()
                .filter(vacancy -> vacancy.getCity() != null && !vacancy.getCity().isBlank())
                .collect(Collectors.groupingByConcurrent(
                        vacancy -> vacancy.getCity().trim(),
                        Collectors.counting()));

        Map<String, Long> byCompany = all.parallelStream()
                .filter(vacancy -> vacancy.getCompany() != null && !vacancy.getCompany().isBlank())
                .collect(Collectors.groupingByConcurrent(
                        vacancy -> vacancy.getCompany().trim(),
                        Collectors.counting()));

        DoubleSummaryStatistics salaries = all.parallelStream()
                .map(StatsService::effectiveSalary)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .summaryStatistics();

        Map<String, Long> bySkill = all.parallelStream()
                .filter(vacancy -> vacancy.getKeySkills() != null && !vacancy.getKeySkills().isBlank())
                .flatMap(vacancy -> Arrays.stream(vacancy.getKeySkills().split(",")))
                .map(String::trim)
                .filter(skill -> !skill.isEmpty())
                .collect(Collectors.groupingByConcurrent(
                        skill -> skill,
                        Collectors.counting()));

        List<StatsResponse.SkillCount> topSkills = bySkill.entrySet().parallelStream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(TOP_LIMIT)
                .map(entry -> new StatsResponse.SkillCount(entry.getKey(), entry.getValue()))
                .toList();

        StatsResponse.SalaryStats salaryStats = new StatsResponse.SalaryStats(
                salaries.getCount(),
                salaries.getCount() == 0 ? 0.0 : round(salaries.getAverage()),
                salaries.getCount() == 0 ? 0.0 : salaries.getMin(),
                salaries.getCount() == 0 ? 0.0 : salaries.getMax()
        );

        return new StatsResponse(
                all.size(),
                topN(byCity, TOP_LIMIT),
                topN(byCompany, TOP_LIMIT),
                salaryStats,
                topSkills
        );
    }

    private static Double effectiveSalary(Vacancy vacancy) {
        if (vacancy.getSalaryFrom() == null && vacancy.getSalaryTo() == null) {
            return null;
        }
        if (vacancy.getSalaryFrom() == null) {
            return vacancy.getSalaryTo();
        }
        if (vacancy.getSalaryTo() == null) {
            return vacancy.getSalaryFrom();
        }
        return Math.max(vacancy.getSalaryFrom(), vacancy.getSalaryTo());
    }

    private static Map<String, Long> topN(Map<String, Long> source, int limit) {
        return source.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                        .thenComparing(Map.Entry.comparingByKey(Comparator.naturalOrder())))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
