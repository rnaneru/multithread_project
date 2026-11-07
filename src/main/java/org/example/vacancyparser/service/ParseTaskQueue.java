package org.example.vacancyparser.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.example.vacancyparser.config.ParserProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

@Component
@Slf4j
public class ParseTaskQueue {

    private final Queue<Runnable> tasks = new ArrayDeque<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final int capacity;

    private volatile boolean stopped;

    public ParseTaskQueue(ParserProperties properties) {
        this.capacity = Math.max(1, properties.executorQueueCapacity());
    }

    public void put(Runnable task) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (tasks.size() >= capacity && !stopped) {
                notFull.await();
            }
            if (stopped) {
                throw new RejectedExecutionException("Очередь задач парсинга остановлена");
            }
            tasks.add(task);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public Optional<Runnable> take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (tasks.isEmpty() && !stopped) {
                notEmpty.await();
            }
            if (tasks.isEmpty()) {
                return Optional.empty();
            }
            Runnable task = tasks.poll();
            notFull.signal();
            return Optional.ofNullable(task);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return tasks.size();
        } finally {
            lock.unlock();
        }
    }

    public int capacity() {
        return capacity;
    }

    @PreDestroy
    public void stop() {
        lock.lock();
        try {
            stopped = true;
            notEmpty.signalAll();
            notFull.signalAll();
            log.info("Очередь задач парсинга остановлена, необработанных задач: {}", tasks.size());
        } finally {
            lock.unlock();
        }
    }
}
