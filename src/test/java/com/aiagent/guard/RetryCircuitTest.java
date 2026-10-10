package com.aiagent.guard;

import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class RetryCircuitTest {
    @Test void retriesAndEventuallyFails() {
        RetryCircuit circuit = new RetryCircuit();
        assertThrows(RuntimeException.class, () -> circuit.execute(() -> { throw new IllegalStateException("down"); }));
    }
}
