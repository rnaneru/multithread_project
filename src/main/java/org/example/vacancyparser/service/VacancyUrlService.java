package org.example.vacancyparser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.vacancyparser.dto.VacancyDto;
import org.example.vacancyparser.hh.HhClient;
import org.example.vacancyparser.hh.HhClientException;
import org.example.vacancyparser.hh.HhVacancyDetails;
import org.example.vacancyparser.hh.HhVacancyDetailsParser;
import org.example.vacancyparser.hh.ParsedVacancy;
import org.example.vacancyparser.repository.VacancyRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacancyUrlService {

    private static final Pattern VACANCY_URL = Pattern.compile(
            "^https?://(?:[a-z0-9-]+\\.)?hh\\.ru/vacancy/(\\d+)(?:[/?#].*)?$",
            Pattern.CASE_INSENSITIVE);

    private final HhClient hhClient;
    private final HhVacancyDetailsParser detailsParser;
    private final VacancyIngestionService ingestionService;
    private final VacancyRepository repository;

    public VacancyDto parseAndSave(String url) {
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("url не должен быть пустым");
        }

        String normalized = url.trim();
        Matcher matcher = VACANCY_URL.matcher(normalized);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Ожидается ссылка вида https://hh.ru/vacancy/123456 или https://tyumen.hh.ru/vacancy/123456");
        }

        String externalId = matcher.group(1);
        String canonicalUrl = "https://hh.ru/vacancy/" + externalId;

        String html = hhClient.fetchVacancyPage(canonicalUrl);
        HhVacancyDetails details = detailsParser.parse(html, canonicalUrl);
        if (details.title() == null || details.title().isBlank()) {
            throw new HhClientException("Не удалось извлечь название вакансии со страницы " + canonicalUrl);
        }

        ParsedVacancy parsed = new ParsedVacancy(
                externalId,
                details.title(),
                details.company(),
                details.city(),
                details.salaryFrom(),
                details.salaryTo(),
                details.salaryCurrency(),
                details.salaryRaw(),
                details.requirements(),
                details.description(),
                details.keySkills(),
                details.publishedAt(),
                canonicalUrl
        );

        ingestionService.saveAll(List.of(parsed));
        log.info("Вакансия {} разобрана по ссылке и сохранена", externalId);

        return repository.findByExternalId(externalId)
                .map(VacancyQueryService::toDto)
                .orElseThrow(() -> new HhClientException("Вакансия не сохранилась: " + externalId));
    }
}
