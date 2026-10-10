package com.aiagent.guard;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class RunLimitsTest {
    @Test void rejectsOversizedInput() {
        RunLimits limits = new RunLimits(12, 3, 4);
        assertThrows(IllegalArgumentException.class, () -> limits.checkInput("12345"));
    }
    @Test void acceptsInputWithinBudget() {
        assertDoesNotThrow(() -> RunLimits.defaults().checkInput("hello"));
    }
}
