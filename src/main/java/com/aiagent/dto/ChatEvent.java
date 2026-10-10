package com.aiagent.dto;

import java.util.Map;

/** Public, deliberately limited view of an internal graph node event. */
public record ChatEvent(
        String type,
        String node,
        String userId,
        String sessionId,
        Map<String, Object> state
) {
    public ChatEvent {
        state = state == null ? Map.of() : Map.copyOf(state);
    }
}
