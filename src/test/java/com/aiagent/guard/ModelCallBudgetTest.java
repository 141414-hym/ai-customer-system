package com.aiagent.guard;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ModelCallBudgetTest {
    @Test void limitsCallsPerTrace() {
        ModelCallBudget budget = new ModelCallBudget();
        for (int i = 0; i < 6; i++) assertTrue(budget.tryAcquire("t-1"));
        assertFalse(budget.tryAcquire("t-1"));
        assertTrue(budget.tryAcquire("t-2"));
    }
}
