package org.example.vacancyparser.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "vacancies", indexes = {
        @Index(name = "idx_vacancies_city", columnList = "city"),
        @Index(name = "idx_vacancies_published_at", columnList = "published_at"),
        @Index(name = "idx_vacancies_company", columnList = "company")
})
@Getter
@Setter
@NoArgsConstructor
public class Vacancy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, unique = true, length = 64)
    private String externalId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(length = 500)
    private String company;

    @Column(length = 200)
    private String city;

    @Column(name = "salary_from")
    private Double salaryFrom;

    @Column(name = "salary_to")
    private Double salaryTo;

    @Column(name = "salary_currency", length = 16)
    private String salaryCurrency;

    @Column(name = "salary_raw", length = 300)
    private String salaryRaw;

    @Column(length = 2000)
    private String requirements;

    @Column(length = 4000)
    private String description;

    @Column(name = "key_skills", length = 2000)
    private String keySkills;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(length = 1000)
    private String url;

    @Column(nullable = false, length = 32)
    private String source;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;
}
