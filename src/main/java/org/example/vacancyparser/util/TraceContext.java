package org.example.vacancyparser.util;

import java.util.concurrent.Callable;
import java.util.function.Supplier;

public final class TraceContext {

    private static final ThreadLocal<String> TRACE_ID = new ThreadLocal<>();

    private TraceContext() {
    }

    public static void set(String traceId) {
        if (traceId == null) {
            TRACE_ID.remove();
        } else {
            TRACE_ID.set(traceId);
        }
    }

    public static String current() {
        return TRACE_ID.get();
    }

    public static void clear() {
        TRACE_ID.remove();
    }

    public static Runnable wrap(Runnable task) {
        String traceId = current();
        return () -> {
            String previous = current();
            set(traceId);
            try {
                task.run();
            } finally {
                set(previous);
            }
        };
    }

    public static <T> Supplier<T> wrap(Supplier<T> task) {
        String traceId = current();
        return () -> {
            String previous = current();
            set(traceId);
            try {
                return task.get();
            } finally {
                set(previous);
            }
        };
    }

    public static <T> Callable<T> wrapCallable(Callable<T> task) {
        String traceId = current();
        return () -> {
            String previous = current();
            set(traceId);
            try {
                return task.call();
            } finally {
                set(previous);
            }
        };
    }
}
