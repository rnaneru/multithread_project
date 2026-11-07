package org.example.vacancyparser.dto;

public record VacancyQuery(
        int page,
        int size,
        String sort,
        String direction,
        String city,
        Double minSalary,
        String search
) {
}
