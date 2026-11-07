package org.example.vacancyparser.hh;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SalaryParserTest {

    @Test
    void parsesFromOnly() {
        SalaryParser.Salary salary = SalaryParser.parse("от 100 000 ₽");

        assertThat(salary.from()).isEqualTo(100_000);
        assertThat(salary.to()).isNull();
        assertThat(salary.currency()).isEqualTo("RUB");
    }

    @Test
    void parsesToOnly() {
        SalaryParser.Salary salary = SalaryParser.parse("до 200 000 ₽");

        assertThat(salary.from()).isNull();
        assertThat(salary.to()).isEqualTo(200_000);
        assertThat(salary.currency()).isEqualTo("RUB");
    }

    @Test
    void parsesRange() {
        SalaryParser.Salary salary = SalaryParser.parse("100 000 – 150 000 ₽");

        assertThat(salary.from()).isEqualTo(100_000);
        assertThat(salary.to()).isEqualTo(150_000);
    }

    @Test
    void parsesUsd() {
        SalaryParser.Salary salary = SalaryParser.parse("$1 500");

        assertThat(salary.from()).isEqualTo(1_500);
        assertThat(salary.to()).isEqualTo(1_500);
        assertThat(salary.currency()).isEqualTo("USD");
    }

    @Test
    void ignoresMissingSalary() {
        SalaryParser.Salary salary = SalaryParser.parse("не указан");

        assertThat(salary.from()).isNull();
        assertThat(salary.to()).isNull();
        assertThat(salary.currency()).isNull();
    }
}
