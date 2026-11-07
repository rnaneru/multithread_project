package org.example.vacancyparser.dto;

import java.time.Instant;
import java.util.List;

public record ParseJobView(
        String jobId,
        String query,
        int area,
        int requestedPages,
        boolean enrichDetails,
        String status,
        Instant startedAt,
        Instant finishedAt,
        long durationMs,
        int pagesFetched,
        int rssItems,
        int uniqueItems,
        int detailsFetched,
        int detailsFailed,
        int created,
        int updated,
        List<String> warnings,
        String error
) {
}
