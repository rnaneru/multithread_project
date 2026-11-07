package org.example.vacancyparser.service;

import org.example.vacancyparser.dto.SaveResult;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class VacancyIngestionMonitor {

    private final Object monitor = new Object();

    private long version;
    private long lastCreated;
    private long lastUpdated;

    public void onIngested(SaveResult result) {
        synchronized (monitor) {
            version++;
            lastCreated = result.created();
            lastUpdated = result.updated();
            monitor.notifyAll();
        }
    }

    public long version() {
        synchronized (monitor) {
            return version;
        }
    }

    public Optional<IngestionEvent> awaitAfter(long knownVersion, long timeoutMs) throws InterruptedException {
        synchronized (monitor) {
            if (version > knownVersion) {
                return Optional.of(snapshot(true));
            }

            long deadline = System.currentTimeMillis() + Math.max(1, timeoutMs);
            while (version <= knownVersion) {
                long remaining = deadline - System.currentTimeMillis();
                if (remaining <= 0) {
                    return Optional.empty();
                }
                monitor.wait(remaining);
            }
            return Optional.of(snapshot(true));
        }
    }

    public IngestionEvent timeoutEvent() {
        synchronized (monitor) {
            return snapshot(false);
        }
    }

    private IngestionEvent snapshot(boolean arrived) {
        return new IngestionEvent(version, lastCreated, lastUpdated, arrived);
    }

    public record IngestionEvent(long version, long created, long updated, boolean arrived) {
    }
}
