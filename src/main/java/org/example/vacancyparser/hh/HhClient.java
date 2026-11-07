package org.example.vacancyparser.hh;

import lombok.extern.slf4j.Slf4j;
import org.example.vacancyparser.config.ParserProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Component
@Slf4j
public class HhClient {

    private static final int PER_PAGE = 20;

    private final WebClient webClient;
    private final ParserProperties properties;

    public HhClient(WebClient hhWebClient, ParserProperties properties) {
        this.webClient = hhWebClient;
        this.properties = properties;
    }

    public String fetchRssPage(String query, int area, int page) {
        String url = UriComponentsBuilder.fromUriString(properties.rssBaseUrl())
                .queryParam("text", query)
                .queryParam("area", area)
                .queryParam("per_page", PER_PAGE)
                .queryParam("page", page)
                .build()
                .encode()
                .toUriString();
        return fetchWithRetries(url, "RSS-страница " + page);
    }

    public String fetchVacancyPage(String url) {
        return fetchWithRetries(url, "вакансия " + url);
    }

    private String fetchWithRetries(String url, String description) {
        int attempts = Math.max(1, properties.maxAttempts());
        RuntimeException lastError = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                String body = webClient.get()
                        .uri(url)
                        .exchangeToMono(response -> {
                            if (response.statusCode().is2xxSuccessful()) {
                                return response.bodyToMono(String.class);
                            }
                            HttpStatus status = HttpStatus.resolve(response.statusCode().value());
                            return response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(payload -> Mono.error(new HhClientException(
                                            "hh.ru вернул " + response.statusCode()
                                                    + (status != null ? " (" + status.getReasonPhrase() + ")" : "")
                                                    + ": " + truncate(payload, 300))));
                        })
                        .block(Duration.ofMillis(properties.requestTimeoutMs() + 5_000L));

                if (body == null || body.isBlank()) {
                    throw new HhClientException("hh.ru вернул пустой ответ для " + description);
                }
                return body;
            } catch (HhClientException e) {
                lastError = e;
                log.warn("Попытка {}/{} не удалась для {}: {}", attempt, attempts, description, e.getMessage());
            } catch (RuntimeException e) {
                lastError = new HhClientException("Ошибка запроса к hh.ru (" + description + ")", e);
                log.warn("Попытка {}/{} не удалась для {}: {}", attempt, attempts, description, e.getMessage());
            }

            if (attempt < attempts) {
                sleep(200L * attempt);
            }
        }

        throw lastError != null
                ? lastError
                : new HhClientException("Не удалось загрузить " + description);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new HhClientException("Запрос к hh.ru прерван", e);
        }
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
