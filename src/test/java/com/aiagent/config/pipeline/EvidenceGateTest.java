package com.aiagent.config.pipeline;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EvidenceGateTest {
    private final EvidenceGate gate = new EvidenceGate();
    @Test void rejectsEmptyRetrieval() {
        var d = gate.assess("【检索结果为空】");
        assertFalse(d.sufficient()); assertEquals("NO_EVIDENCE", d.reason());
    }
    @Test void acceptsSourcedEvidence() {
        var d = gate.assess("来源: xp_specs.md\nG6 参数");
        assertTrue(d.sufficient()); assertEquals(1, d.count());
    }
}
