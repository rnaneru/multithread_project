package org.example.vacancyparser.service;

import org.example.vacancyparser.dto.SaveResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VacancyIngestionMonitorTest {

    @Test
    void versionAdvancesAndWaitersSeeNewIngestion() throws Exception {
        VacancyIngestionMonitor monitor = new VacancyIngestionMonitor();

        assertThat(monitor.version()).isZero();
        assertThat(monitor.awaitAfter(0, 5)).isEmpty();

        monitor.onIngested(new SaveResult(2, 3, 5));

        var event = monitor.awaitAfter(0, 1_000).orElseThrow();
        assertThat(event.arrived()).isTrue();
        assertThat(event.version()).isEqualTo(1);
        assertThat(event.created()).isEqualTo(2);
        assertThat(event.updated()).isEqualTo(3);
    }
}
