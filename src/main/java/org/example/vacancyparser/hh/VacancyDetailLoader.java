package org.example.vacancyparser.hh;

import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public interface VacancyDetailLoader {

    String mode();

    Map<String, HhVacancyDetails> load(List<HhRssItem> items,
                                       int concurrency,
                                       long delayMs,
                                       BiConsumer<String, Throwable> onFailure);
}
