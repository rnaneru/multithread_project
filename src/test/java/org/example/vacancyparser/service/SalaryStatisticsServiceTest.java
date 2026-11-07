package org.example.vacancyparser.service;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryStatisticsServiceTest {

    @Test
    void recursiveTaskCalculatesStatistics() {
        double[] salaries = IntStream.rangeClosed(1, 1_000)
                .mapToDouble(value -> value)
                .toArray();

        var task = new SalaryStatisticsService.SalaryTask(salaries, 0, salaries.length);
        var statistics = task.compute();

        assertThat(statistics.count()).isEqualTo(1_000);
        assertThat(statistics.min()).isEqualTo(1.0);
        assertThat(statistics.max()).isEqualTo(1_000.0);
        assertThat(statistics.average()).isEqualTo(500.5);
        assertThat(statistics.total()).isEqualTo(500_500.0);
    }

    @Test
    void emptyArrayProducesEmptyStatistics() {
        var task = new SalaryStatisticsService.SalaryTask(new double[0], 0, 0);

        var statistics = task.compute();

        assertThat(statistics.count()).isZero();
        assertThat(statistics.average()).isZero();
    }
}
