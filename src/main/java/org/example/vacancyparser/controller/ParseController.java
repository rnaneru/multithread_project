package org.example.vacancyparser.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.example.vacancyparser.config.ParserProperties;
import org.example.vacancyparser.dto.ParseJobView;
import org.example.vacancyparser.dto.ParseRequest;
import org.example.vacancyparser.dto.VacancyDto;
import org.example.vacancyparser.service.ParseJobService;
import org.example.vacancyparser.service.VacancyUrlService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/parse")
@RequiredArgsConstructor
public class ParseController {

    private final ParseJobService parseJobService;
    private final VacancyUrlService vacancyUrlService;
    private final ParserProperties properties;

    @PostMapping("/vacancy")
    public ResponseEntity<VacancyDto> parseVacancy(@RequestParam String url) {
        return ResponseEntity.ok(vacancyUrlService.parseAndSave(url));
    }

    @PostMapping
    public ResponseEntity<ParseJobView> start(@Valid @RequestBody ParseRequest request) {
        return ResponseEntity.accepted().body(parseJobService.submit(request));
    }

    @PostMapping("/run")
    public ResponseEntity<ParseJobView> run(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer area,
            @RequestParam(required = false) Integer pages,
            @RequestParam(required = false) Boolean enrichDetails) {

        ParseRequest request = new ParseRequest(
                query == null || query.isBlank() ? properties.defaultQuery() : query,
                area,
                pages,
                enrichDetails
        );
        return ResponseEntity.accepted().body(parseJobService.submit(request));
    }

    @PostMapping("/run-sync")
    public ResponseEntity<ParseJobView> runSync(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) Integer area,
            @RequestParam(required = false) Integer pages,
            @RequestParam(required = false) Boolean enrichDetails) {

        ParseRequest request = new ParseRequest(
                query == null || query.isBlank() ? properties.defaultQuery() : query,
                area,
                pages,
                enrichDetails
        );
        ParseJobView started = parseJobService.submit(request);
        ParseJobView result = parseJobService.waitFor(started.jobId(), properties.syncTimeoutMs())
                .orElse(started);
        return ResponseEntity.status(HttpStatus.OK).body(result);
    }

    @GetMapping("/jobs")
    public List<ParseJobView> jobs() {
        return parseJobService.findAll();
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ParseJobView> job(@PathVariable String jobId) {
        return parseJobService.find(jobId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
