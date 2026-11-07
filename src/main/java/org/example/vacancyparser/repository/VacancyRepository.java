package org.example.vacancyparser.repository;

import org.example.vacancyparser.domain.Vacancy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface VacancyRepository extends JpaRepository<Vacancy, Long> {

    Optional<Vacancy> findByExternalId(String externalId);

    List<Vacancy> findAllByExternalIdIn(Collection<String> externalIds);

    long countByCity(String city);
}
