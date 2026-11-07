package org.example.vacancyparser.hh;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VacancyDetailFetcher {

    private final HhClient hhClient;
    private final HhVacancyDetailsParser detailsParser;

    public HhVacancyDetails fetch(HhRssItem item, long delayMs) throws InterruptedException {
        if (delayMs > 0) {
            Thread.sleep(delayMs);
        }
        String html = hhClient.fetchVacancyPage(item.url());
        return detailsParser.parse(html, item.url());
    }
}
