package org.example.vacancyparser.dto;

import java.time.Instant;

public record VacancyDto(
        Long id,
        String externalId,
        String title,
        String company,
        String city,
        Double salaryFrom,
        Double salaryTo,
        String salaryCurrency,
        String salaryRaw,
        String requirements,
        String description,
        String keySkills,
        Instant publishedAt,
        Instant fetchedAt,
        String url,
        String source
) {
}
