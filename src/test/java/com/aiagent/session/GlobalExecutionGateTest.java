package com.aiagent.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import reactor.core.publisher.Flux;
import org.junit.jupiter.api.Test;

class GlobalExecutionGateTest {
    @Test void executesTask() {
        assertEquals(1, new GlobalExecutionGate().execute(() -> Flux.just(1)).collectList().block().size());
    }
}
