package com.aiagent.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class ChatMetrics {
    private final MeterRegistry registry;
    public ChatMetrics(MeterRegistry registry) { this.registry = registry; }
    public void completed(String mode, long elapsedNanos) {
        Counter.builder("aiagent.chat.requests").tag("mode", mode).tag("outcome", "completed").register(registry).increment();
        Timer.builder("aiagent.chat.duration").tag("mode", mode).register(registry).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }
    public void failed(String mode) {
        Counter.builder("aiagent.chat.requests").tag("mode", mode).tag("outcome", "failed").register(registry).increment();
    }
}
