package org.example.vacancyparser.hh;

import java.time.Instant;

public record HhRssItem(
        String externalId,
        String title,
        String company,
        String city,
        String salaryRaw,
        Double salaryFrom,
        Double salaryTo,
        String salaryCurrency,
        Instant publishedAt,
        String url
) {
}
