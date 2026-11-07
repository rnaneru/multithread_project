package org.example.vacancyparser.hh;

import java.time.Instant;

public record ParsedVacancy(
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
        String url
) {

    public static ParsedVacancy fromRss(HhRssItem item) {
        return new ParsedVacancy(
                item.externalId(),
                item.title(),
                item.company(),
                item.city(),
                item.salaryFrom(),
                item.salaryTo(),
                item.salaryCurrency(),
                item.salaryRaw(),
                null,
                null,
                null,
                item.publishedAt(),
                item.url()
        );
    }

    public ParsedVacancy withDetails(HhVacancyDetails details) {
        if (details == null) {
            return this;
        }
        return new ParsedVacancy(
                externalId,
                prefer(details.title(), title),
                prefer(details.company(), company),
                prefer(details.city(), city),
                prefer(details.salaryFrom(), salaryFrom),
                prefer(details.salaryTo(), salaryTo),
                prefer(details.salaryCurrency(), salaryCurrency),
                prefer(details.salaryRaw(), salaryRaw),
                prefer(details.requirements(), requirements),
                prefer(details.description(), description),
                prefer(details.keySkills(), keySkills),
                prefer(details.publishedAt(), publishedAt),
                url
        );
    }

    public boolean hasRequirements() {
        return requirements != null && !requirements.isBlank();
    }

    private static <T> T prefer(T primary, T fallback) {
        return primary != null ? primary : fallback;
    }
}
