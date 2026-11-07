package org.example.vacancyparser.dto;

import java.util.List;
import java.util.Map;

public record StatsResponse(
        long totalVacancies,
        Map<String, Long> byCity,
        Map<String, Long> topCompanies,
        SalaryStats salary,
        List<SkillCount> topSkills
) {
    public record SalaryStats(long withSalary, double average, double min, double max) {
    }

    public record SkillCount(String skill, long count) {
    }
}
