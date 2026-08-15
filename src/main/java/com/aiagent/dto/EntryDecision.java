package com.aiagent.dto;

import java.util.Map;

public record EntryDecision(String route,
                            double confidence,
                            String decisionReason,
                            Map<String, Object> trace) {
}
