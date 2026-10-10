package com.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RedisContextStoreTest {
    @Test
    void buildsUserScopedKeys() {
        assertEquals("chat:context:u-1:c-1:meta", RedisContextStore.metaKey("u-1", "c-1"));
        assertEquals("chat:context:u-1:c-1:window", RedisContextStore.windowKey("u-1", "c-1"));
    }
}
