package com.aiagent.guard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class ToolCallGuard {
    private static final int MAX_CALLS = 3;
    private final ConcurrentHashMap<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    private final Set<String> executed = ConcurrentHashMap.newKeySet();

    public Decision check(String runId, String toolName, String arguments) {
        String key = runId + ":" + toolName;
        int count = counts.computeIfAbsent(key, ignored -> new AtomicInteger()).incrementAndGet();
        String fingerprint = key + ":" + arguments;
        if (!executed.add(fingerprint)) return new Decision(false, "DUPLICATE_CALL");
        if (count > MAX_CALLS) return new Decision(false, "TOOL_BUDGET_EXCEEDED");
        return new Decision(true, "OK");
    }

    public record Decision(boolean allowed, String reason) {}
}
