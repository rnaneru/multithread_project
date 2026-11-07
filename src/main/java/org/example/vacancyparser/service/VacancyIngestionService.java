package org.example.vacancyparser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.vacancyparser.domain.Vacancy;
import org.example.vacancyparser.dto.SaveResult;
import org.example.vacancyparser.hh.ParsedVacancy;
import org.example.vacancyparser.repository.VacancyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class VacancyIngestionService {

    private final VacancyRepository repository;
    private final VacancyIngestionMonitor ingestionMonitor;
    private final StatsService statsService;

    @Transactional
    public SaveResult saveAll(Collection<ParsedVacancy> parsedVacancies) {
        if (parsedVacancies == null || parsedVacancies.isEmpty()) {
            return new SaveResult(0, 0, 0);
        }

        Map<String, ParsedVacancy> unique = new LinkedHashMap<>();
        for (ParsedVacancy vacancy : parsedVacancies) {
            if (vacancy != null && vacancy.externalId() != null) {
                unique.putIfAbsent(vacancy.externalId(), vacancy);
            }
        }
        if (unique.isEmpty()) {
            return new SaveResult(0, 0, 0);
        }

        Map<String, Vacancy> existing = new HashMap<>();
        for (Vacancy vacancy : repository.findAllByExternalIdIn(unique.keySet())) {
            existing.put(vacancy.getExternalId(), vacancy);
        }

        Instant now = Instant.now();
        List<Vacancy> toSave = new ArrayList<>(unique.size());
        int created = 0;
        int updated = 0;

        for (ParsedVacancy parsed : unique.values()) {
            Vacancy entity = existing.get(parsed.externalId());
            if (entity == null) {
                entity = new Vacancy();
                entity.setExternalId(parsed.externalId());
                entity.setCreatedAt(now);
                created++;
            } else {
                updated++;
            }
            apply(entity, parsed, now);
            toSave.add(entity);
        }

        repository.saveAll(toSave);
        log.info("Сохранено вакансий: всего {}, новых {}, обновлённых {}",
                toSave.size(), created, updated);

        SaveResult result = new SaveResult(created, updated, toSave.size());
        afterCommit(() -> {
            statsService.invalidate();
            ingestionMonitor.onIngested(result);
        });
        return result;
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    private void apply(Vacancy entity, ParsedVacancy parsed, Instant now) {
        entity.setTitle(truncate(parsed.title(), 500));
        entity.setCompany(truncate(parsed.company(), 500));
        entity.setCity(truncate(parsed.city(), 200));
        entity.setSalaryFrom(parsed.salaryFrom());
        entity.setSalaryTo(parsed.salaryTo());
        entity.setSalaryCurrency(truncate(parsed.salaryCurrency(), 16));
        entity.setSalaryRaw(truncate(parsed.salaryRaw(), 300));
        entity.setRequirements(truncate(parsed.requirements(), 2000));
        entity.setDescription(truncate(parsed.description(), 4000));
        entity.setKeySkills(truncate(parsed.keySkills(), 2000));
        entity.setPublishedAt(parsed.publishedAt());
        entity.setUrl(truncate(parsed.url(), 1000));
        entity.setSource("hh.ru");
        entity.setUpdatedAt(now);
        entity.setFetchedAt(now);
    }

    public Optional<Vacancy> findById(long id) {
        return repository.findById(id);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
