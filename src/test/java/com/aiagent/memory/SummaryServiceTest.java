package com.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SummaryServiceTest {
    @Test
    void rejectsStaleMysqlVersion() {
        SummaryRepository repo = mock(SummaryRepository.class);
        RedisContextStore redis = mock(RedisContextStore.class);
        when(repo.createIfAbsent("c-1")).thenReturn(new SummaryRepository.SummaryRecord(
                "c-1", "{}", 0, 3, null, null));
        when(repo.updateIfVersion(eq("c-1"), eq(3L), any(), eq(2L), any(), any())).thenReturn(false);

        assertFalse(new SummaryService(repo, redis).persist("u-1", "c-1", "{\"x\":1}",
                2, "test", "v1"));
    }

    @Test
    void updatesRedisAfterMysqlCommit() {
        SummaryRepository repo = mock(SummaryRepository.class);
        RedisContextStore redis = mock(RedisContextStore.class);
        when(repo.createIfAbsent("c-1")).thenReturn(new SummaryRepository.SummaryRecord(
                "c-1", "{}", 0, 3, null, null));
        when(repo.updateIfVersion(eq("c-1"), eq(3L), any(), eq(2L), any(), any())).thenReturn(true);
        when(redis.updateSummaryIfVersion("u-1", "c-1", 3, "{\"x\":1}", 4, 2)).thenReturn(true);

        assertTrue(new SummaryService(repo, redis).persist("u-1", "c-1", "{\"x\":1}",
                2, "test", "v1"));
    }
}
