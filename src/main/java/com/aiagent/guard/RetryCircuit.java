package com.aiagent.guard;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class RetryCircuit {
    private static final int FAILURE_THRESHOLD = 3;
    private final AtomicInteger failures = new AtomicInteger();
    private volatile long openUntil;

    public boolean allow() { return System.currentTimeMillis() >= openUntil; }

    public <T> T execute(java.util.function.Supplier<T> action) {
        if (!allow()) throw new IllegalStateException("下游服务熔断中");
        RuntimeException last = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                T value = action.get();
                failures.set(0);
                return value;
            } catch (RuntimeException ex) {
                last = ex;
            }
        }
        if (failures.incrementAndGet() >= FAILURE_THRESHOLD) openUntil = System.currentTimeMillis() + Duration.ofSeconds(10).toMillis();
        throw last;
    }
}
