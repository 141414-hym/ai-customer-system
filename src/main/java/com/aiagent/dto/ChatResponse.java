package com.aiagent.dto;

import java.util.List;

public record ChatResponse(
        String sessionId,
        String route,
        String answer,
        double confidence,
        List<Citation> citations,
        boolean handoffRequired,
        String handoffReason
) {
    public record Citation(String sourceId, String title, String snippet, String url) {
    }
}
