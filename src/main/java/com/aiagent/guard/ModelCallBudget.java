package com.aiagent.guard;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ModelCallBudget {
    private static final int MAX_CALLS = 6;
    private final ConcurrentHashMap<String, AtomicInteger> calls = new ConcurrentHashMap<>();

    public boolean tryAcquire(String traceId) {
        String key = traceId == null || traceId.isBlank() ? "unknown" : traceId;
        AtomicInteger count = calls.computeIfAbsent(key, ignored -> new AtomicInteger());
        if (count.incrementAndGet() > MAX_CALLS) {
            count.decrementAndGet();
            return false;
        }
        return true;
    }

    public int current(String traceId) {
        AtomicInteger value = calls.get(traceId);
        return value == null ? 0 : value.get();
    }
}
