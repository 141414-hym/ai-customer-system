package com.aiagent.config.state;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class GraphExecutionGuardTest {
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochMilli(1_000), ZoneOffset.UTC);

    @Test
    void allowsStateWithinLimits() {
        Map<String, Object> state = new HashMap<>();
        state.put(AgentStateKeys.NODE_COUNT, 2);
        state.put(AgentStateKeys.DEADLINE_AT, 2_000L);
        assertEquals(GraphExecutionGuard.Decision.ALLOWED, GraphExecutionGuard.check(state, 12, CLOCK));
        assertEquals(3, GraphExecutionGuard.nextNodeCount(state));
    }

    @Test
    void rejectsStepLimitAndExpiredDeadline() {
        Map<String, Object> state = new HashMap<>();
        state.put(AgentStateKeys.NODE_COUNT, 12);
        assertEquals(GraphExecutionGuard.Decision.STEP_LIMIT, GraphExecutionGuard.check(state, 12, CLOCK));
        state.put(AgentStateKeys.NODE_COUNT, 1);
        state.put(AgentStateKeys.DEADLINE_AT, 999L);
        assertEquals(GraphExecutionGuard.Decision.DEADLINE_EXCEEDED, GraphExecutionGuard.check(state, 12, CLOCK));
    }

    @Test
    void rejectsMalformedCounters() {
        Map<String, Object> state = Map.of(AgentStateKeys.NODE_COUNT, "bad");
        assertEquals(GraphExecutionGuard.Decision.INVALID_STATE, GraphExecutionGuard.check(state, 12, CLOCK));
    }
}
