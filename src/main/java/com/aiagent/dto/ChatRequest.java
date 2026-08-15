package com.aiagent.dto;

import java.util.List;

public record ChatRequest(
        String sessionId,
        String message,
        String locale,
        String tenantId,
        String productCode,
        List<String> tags
) {
}
