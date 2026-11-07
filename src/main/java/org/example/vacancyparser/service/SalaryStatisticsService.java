package org.example.vacancyparser.service;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import org.example.vacancyparser.domain.Vacancy;
import org.example.vacancyparser.repository.VacancyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SalaryStatisticsService {

    private static final int THRESHOLD = 128;

    private final VacancyRepository repository;

    private final ForkJoinPool forkJoinPool = new ForkJoinPool(
            Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors())));

    public SalaryStatistics compute() {
        List<Vacancy> vacancies = repository.findAll();
        double[] salaries = vacancies.stream()
                .map(SalaryStatisticsService::effectiveSalary)
                .filter(Objects::nonNull)
                .mapToDouble(Double::doubleValue)
                .toArray();

        return forkJoinPool.invoke(new SalaryTask(salaries, 0, salaries.length));
    }

    @PreDestroy
    public void shutdown() {
        forkJoinPool.shutdown();
        try {
            if (!forkJoinPool.awaitTermination(5, TimeUnit.SECONDS)) {
                forkJoinPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            forkJoinPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
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

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    static final class SalaryTask extends RecursiveTask<SalaryStatistics> {

        private final double[] values;
        private final int start;
        private final int end;

        SalaryTask(double[] values, int start, int end) {
            this.values = values;
            this.start = start;
            this.end = end;
        }

        @Override
        protected SalaryStatistics compute() {
            if (end - start <= THRESHOLD) {
                return computeSequentially();
            }

            int middle = (start + end) / 2;
            SalaryTask left = new SalaryTask(values, start, middle);
            SalaryTask right = new SalaryTask(values, middle, end);

            left.fork();
            SalaryStatistics rightResult = right.compute();
            SalaryStatistics leftResult = left.join();
            return leftResult.combine(rightResult);
        }

        private SalaryStatistics computeSequentially() {
            if (end <= start) {
                return SalaryStatistics.empty();
            }

            long count = 0;
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            double total = 0.0;

            for (int i = start; i < end; i++) {
                double value = values[i];
                count++;
                min = Math.min(min, value);
                max = Math.max(max, value);
                total += value;
            }

            return new SalaryStatistics(count, round(min), round(max), round(total / count), round(total));
        }
    }

    public record SalaryStatistics(long count, double min, double max, double average, double total) {

        public static SalaryStatistics empty() {
            return new SalaryStatistics(0, 0.0, 0.0, 0.0, 0.0);
        }

        public SalaryStatistics combine(SalaryStatistics other) {
            if (other.count == 0) {
                return this;
            }
            if (this.count == 0) {
                return other;
            }

            long count = this.count + other.count;
            double total = this.total + other.total;
            return new SalaryStatistics(
                    count,
                    round(Math.min(this.min, other.min)),
                    round(Math.max(this.max, other.max)),
                    round(total / count),
                    round(total)
            );
        }
    }
}
