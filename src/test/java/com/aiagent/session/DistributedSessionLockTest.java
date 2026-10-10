package com.aiagent.session;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DistributedSessionLockTest {
    @Test void threadIdIsStableForLockKey() {
        SessionKey key = new SessionKey("u", "s");
        assertTrue(("agent:session:lock:" + key.threadId()).startsWith("agent:session:lock:"));
    }
}
