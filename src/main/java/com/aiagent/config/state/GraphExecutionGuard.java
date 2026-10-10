package com.aiagent.config.state;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;

/** Deterministic execution limits used by Graph nodes and conditional edges. */
public final class GraphExecutionGuard {
    private GraphExecutionGuard() {}

    public static Decision check(Map<String, Object> state, int maxNodes, Clock clock) {
        int nodeCount = number(state.get(AgentStateKeys.NODE_COUNT));
        int modelCalls = number(state.get(AgentStateKeys.MODEL_CALL_COUNT));
        long deadline = longNumber(state.get(AgentStateKeys.DEADLINE_AT));
        if (nodeCount >= maxNodes) return Decision.STEP_LIMIT;
        if (deadline > 0 && clock.instant().isAfter(Instant.ofEpochMilli(deadline))) {
            return Decision.DEADLINE_EXCEEDED;
        }
        if (modelCalls < 0 || nodeCount < 0) return Decision.INVALID_STATE;
        return Decision.ALLOWED;
    }

    public static int nextNodeCount(Map<String, Object> state) {
        return number(state.get(AgentStateKeys.NODE_COUNT)) + 1;
    }

    private static int number(Object value) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return 0;
        try { return Integer.parseInt(String.valueOf(value)); }
        catch (NumberFormatException e) { return -1; }
    }

    private static long longNumber(Object value) {
        if (value instanceof Number n) return n.longValue();
        if (value == null) return 0;
        try { return Long.parseLong(String.valueOf(value)); }
        catch (NumberFormatException e) { return -1; }
    }

    public enum Decision { ALLOWED, STEP_LIMIT, DEADLINE_EXCEEDED, INVALID_STATE }
}
