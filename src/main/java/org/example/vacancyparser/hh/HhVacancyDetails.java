package org.example.vacancyparser.hh;

import java.time.Instant;

public record HhVacancyDetails(
        String title,
        String company,
        String city,
        String salaryRaw,
        Double salaryFrom,
        Double salaryTo,
        String salaryCurrency,
        String requirements,
        String description,
        String keySkills,
        Instant publishedAt
) {
}
