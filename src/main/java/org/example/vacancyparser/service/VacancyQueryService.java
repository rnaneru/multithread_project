package org.example.vacancyparser.service;

import lombok.RequiredArgsConstructor;
import org.example.vacancyparser.domain.Vacancy;
import org.example.vacancyparser.dto.PageResponse;
import org.example.vacancyparser.dto.VacancyDto;
import org.example.vacancyparser.dto.VacancyQuery;
import org.example.vacancyparser.repository.VacancyRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class VacancyQueryService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 200;

    private final VacancyRepository repository;

    public PageResponse<VacancyDto> find(VacancyQuery query) {
        int page = Math.max(0, query.page());
        int size = query.size() <= 0 ? DEFAULT_PAGE_SIZE : Math.min(query.size(), MAX_PAGE_SIZE);

        List<Vacancy> all = repository.findAll();
        Predicate<Vacancy> filter = buildFilter(query);

        List<VacancyDto> filtered = all.parallelStream()
                .filter(filter)
                .sorted(comparator(query.sort(), query.direction()))
                .map(VacancyQueryService::toDto)
                .toList();

        long total = filtered.size();
        int from = (int) Math.min((long) page * size, total);
        int to = Math.min(from + size, filtered.size());
        List<VacancyDto> content = filtered.subList(from, to);

        return PageResponse.of(content, page, size, total);
    }

    public List<VacancyDto> findTop(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 1000));
        return repository.findAll().parallelStream()
                .sorted(Comparator.comparing(Vacancy::getPublishedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(safeLimit)
                .map(VacancyQueryService::toDto)
                .toList();
    }

    private static Predicate<Vacancy> buildFilter(VacancyQuery query) {
        Predicate<Vacancy> predicate = vacancy -> true;

        if (query.city() != null && !query.city().isBlank()) {
            String city = query.city().trim().toLowerCase(Locale.ROOT);
            predicate = predicate.and(vacancy -> vacancy.getCity() != null
                    && vacancy.getCity().toLowerCase(Locale.ROOT).contains(city));
        }

        if (query.minSalary() != null) {
            double minSalary = query.minSalary();
            predicate = predicate.and(vacancy -> effectiveSalary(vacancy) != null
                    && effectiveSalary(vacancy) >= minSalary);
        }

        if (query.search() != null && !query.search().isBlank()) {
            String search = query.search().trim().toLowerCase(Locale.ROOT);
            predicate = predicate.and(vacancy ->
                    containsIgnoreCase(vacancy.getTitle(), search)
                            || containsIgnoreCase(vacancy.getCompany(), search)
                            || containsIgnoreCase(vacancy.getRequirements(), search)
                            || containsIgnoreCase(vacancy.getKeySkills(), search));
        }

        return predicate;
    }

    private static Comparator<Vacancy> comparator(String sort, String direction) {
        String field = sort == null ? "publishedAt" : sort.trim();
        Comparator<Vacancy> comparator = switch (field) {
            case "title" -> Comparator.comparing(Vacancy::getTitle,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "company" -> Comparator.comparing(Vacancy::getCompany,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "city" -> Comparator.comparing(Vacancy::getCity,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case "salary" -> Comparator.comparing(VacancyQueryService::effectiveSalary,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            case "createdAt" -> Comparator.comparing(Vacancy::getCreatedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(Vacancy::getPublishedAt,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        };

        boolean ascending = "asc".equalsIgnoreCase(direction);
        return ascending ? comparator : comparator.reversed();
    }

    private static Double effectiveSalary(Vacancy vacancy) {
        Double from = vacancy.getSalaryFrom();
        Double to = vacancy.getSalaryTo();
        if (from == null && to == null) {
            return null;
        }
        if (from == null) {
            return to;
        }
        if (to == null) {
            return from;
        }
        return Math.max(from, to);
    }

    private static boolean containsIgnoreCase(String value, String search) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(search);
    }

    public static VacancyDto toDto(Vacancy vacancy) {
        return new VacancyDto(
                vacancy.getId(),
                vacancy.getExternalId(),
                vacancy.getTitle(),
                vacancy.getCompany(),
                vacancy.getCity(),
                vacancy.getSalaryFrom(),
                vacancy.getSalaryTo(),
                vacancy.getSalaryCurrency(),
                vacancy.getSalaryRaw(),
                vacancy.getRequirements(),
                vacancy.getDescription(),
                vacancy.getKeySkills(),
                vacancy.getPublishedAt(),
                vacancy.getFetchedAt(),
                vacancy.getUrl(),
                vacancy.getSource()
        );
    }
}
