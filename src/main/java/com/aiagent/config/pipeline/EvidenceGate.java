package com.aiagent.config.pipeline;

import org.springframework.stereotype.Component;

@Component
public class EvidenceGate {
    public EvidenceDecision assess(String retrieved) {
        if (retrieved == null || retrieved.isBlank() || retrieved.contains("检索结果为空")) {
            return new EvidenceDecision(false, 0, "NO_EVIDENCE");
        }
        int count = 0, from = 0;
        while ((from = retrieved.indexOf("来源:", from)) >= 0) { count++; from += 3; }
        return count == 0 ? new EvidenceDecision(false, 0, "NO_SOURCED_EVIDENCE")
                : new EvidenceDecision(true, count, "SUFFICIENT");
    }
    public record EvidenceDecision(boolean sufficient, int count, String reason) {}
}
