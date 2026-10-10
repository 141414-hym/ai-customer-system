package com.aiagent.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ToolCallGuardTest {
    @Test void rejectsDuplicateArguments() {
        ToolCallGuard guard = new ToolCallGuard();
        assertTrue(guard.check("r", "order", "ORD123456").allowed());
        assertEquals("DUPLICATE_CALL", guard.check("r", "order", "ORD123456").reason());
    }
}
