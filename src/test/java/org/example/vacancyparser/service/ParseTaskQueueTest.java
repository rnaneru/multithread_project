package org.example.vacancyparser.service;

import org.example.vacancyparser.config.ParserProperties;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ParseTaskQueueTest {

    @Test
    void producerAndConsumerExchangeTasks() throws Exception {
        ParseTaskQueue queue = new ParseTaskQueue(properties(4));
        AtomicInteger counter = new AtomicInteger();

        queue.put(counter::incrementAndGet);
        queue.put(counter::incrementAndGet);

        assertThat(queue.size()).isEqualTo(2);

        queue.take().orElseThrow().run();
        queue.take().orElseThrow().run();

        assertThat(counter).hasValue(2);
        assertThat(queue.size()).isZero();
    }

    @Test
    void stoppedQueueReturnsEmptyTaskAndRejectsNewOnes() throws Exception {
        ParseTaskQueue queue = new ParseTaskQueue(properties(1));
        queue.stop();

        assertThat(queue.take()).isEmpty();
    }

    private static ParserProperties properties(int queueCapacity) {
        return new ParserProperties(
                "https://hh.ru/search/vacancy/rss",
                "https://hh.ru/vacancy",
                "test-agent",
                "Java",
                1,
                1,
                10,
                20,
                4,
                0,
                "virtual",
                25_000,
                3,
                4,
                8,
                queueCapacity,
                1,
                1,
                false,
                false,
                120_000
        );
    }
}
