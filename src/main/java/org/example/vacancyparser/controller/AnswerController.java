package org.example.vacancyparser.controller;

import lombok.RequiredArgsConstructor;
import org.example.vacancyparser.dto.PageResponse;
import org.example.vacancyparser.dto.VacancyDto;
import org.example.vacancyparser.dto.VacancyQuery;
import org.example.vacancyparser.repository.VacancyRepository;
import org.example.vacancyparser.service.VacancyQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AnswerController {

    private final VacancyQueryService queryService;
    private final VacancyRepository repository;

    @GetMapping("/answer")
    public PageResponse<VacancyDto> answer(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "publishedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double minSalary,
            @RequestParam(required = false) String search) {

        VacancyQuery query = new VacancyQuery(page, size, sort, direction, city, minSalary, search);
        return queryService.find(query);
    }

    @GetMapping("/api/vacancies")
    public PageResponse<VacancyDto> vacancies(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "publishedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double minSalary,
            @RequestParam(required = false) String search) {

        return answer(page, size, sort, direction, city, minSalary, search);
    }

    @GetMapping("/api/vacancies/{id}")
    public ResponseEntity<VacancyDto> vacancy(@PathVariable long id) {
        return repository.findById(id)
                .map(VacancyQueryService::toDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/api/vacancies/top")
    public java.util.List<VacancyDto> top(@RequestParam(defaultValue = "10") int limit) {
        return queryService.findTop(limit);
    }

    @GetMapping("/api/vacancies/count")
    public long count() {
        return repository.count();
    }
}
