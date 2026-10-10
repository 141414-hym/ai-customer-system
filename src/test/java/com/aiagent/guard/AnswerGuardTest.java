package com.aiagent.guard;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AnswerGuardTest {
    private final AnswerGuard guard = new AnswerGuard();
    @Test void rejectsSensitivePhone() { assertFalse(guard.validate("联系电话 13800138000").allowed()); }
    @Test void acceptsNormalAnswer() { assertTrue(guard.validate("G6 支持多种车身颜色").allowed()); }
}
