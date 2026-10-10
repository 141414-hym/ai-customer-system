package com.aiagent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Redis hot context: durable history remains in MySQL. */
@Service
public class RedisContextStore {
    private static final String SUMMARY = "summaryJson";
    private static final String SUMMARY_VERSION = "summaryVersion";
    private static final String COVERED_UNTIL = "coveredUntilSequence";
    private static final String LAST_SEQUENCE = "lastSequence";
    private static final String CONTEXT_VERSION = "contextVersion";
    private static final DefaultRedisScript<Boolean> SUMMARY_CAS_SCRIPT = new DefaultRedisScript<>("""
            local v = redis.call('HGET', KEYS[1], 'summaryVersion') or '0'
            if v ~= ARGV[1] then return 0 end
            redis.call('HSET', KEYS[1], 'summaryJson', ARGV[2], 'summaryVersion', ARGV[3],
                       'coveredUntilSequence', ARGV[4])
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', ARGV[4])
            return 1
            """, Boolean.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public RedisContextStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void appendMessage(String userId, String conversationId, long sequenceNo,
                              String role, String content) {
        String metaKey = metaKey(userId, conversationId);
        String windowKey = windowKey(userId, conversationId);
        try {
            String message = objectMapper.writeValueAsString(new ContextMessage(
                    sequenceNo, role, content, Instant.now().toString()));
            redis.opsForZSet().add(windowKey, message, sequenceNo);
            redis.opsForHash().putAll(metaKey, Map.of(
                    LAST_SEQUENCE, Long.toString(sequenceNo),
                    CONTEXT_VERSION, Long.toString(currentVersion(metaKey) + 1)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("无法序列化 Redis 会话消息", e);
        }
    }

    public ContextSnapshot load(String userId, String conversationId) {
        String metaKey = metaKey(userId, conversationId);
        String windowKey = windowKey(userId, conversationId);
        Map<Object, Object> meta = redis.opsForHash().entries(metaKey);
        List<ContextMessage> messages = Objects.requireNonNullElse(
                redis.opsForZSet().range(windowKey, 0, -1), java.util.Set.<String>of())
                .stream().map(this::readMessage).filter(Optional::isPresent)
                .map(Optional::get).toList();
        return new ContextSnapshot(
                (String) meta.getOrDefault(SUMMARY, "{}"),
                longValue(meta.get(COVERED_UNTIL)), longValue(meta.get(LAST_SEQUENCE)),
                longValue(meta.get(CONTEXT_VERSION)), messages);
    }

    public boolean updateSummaryIfVersion(String userId, String conversationId,
                                          long expectedVersion, String summaryJson,
                                          long newVersion, long coveredUntilSequence) {
        Boolean updated = redis.execute(SUMMARY_CAS_SCRIPT,
                List.of(metaKey(userId, conversationId), windowKey(userId, conversationId)),
                Long.toString(expectedVersion), summaryJson, Long.toString(newVersion),
                Long.toString(coveredUntilSequence));
        return Boolean.TRUE.equals(updated);
    }

    public static String metaKey(String userId, String conversationId) {
        return "chat:context:" + userId + ":" + conversationId + ":meta";
    }

    public static String windowKey(String userId, String conversationId) {
        return "chat:context:" + userId + ":" + conversationId + ":window";
    }

    private long currentVersion(String metaKey) {
        return longValue(redis.opsForHash().get(metaKey, CONTEXT_VERSION));
    }

    private Optional<ContextMessage> readMessage(String value) {
        try {
            return Optional.of(objectMapper.readValue(value, ContextMessage.class));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    private static long longValue(Object value) {
        return value == null ? 0L : Long.parseLong(value.toString());
    }

    public record ContextMessage(long sequenceNo, String role, String content, String createdAt) {}

    public record ContextSnapshot(String summaryJson, long coveredUntilSequence,
                                  long lastSequence, long contextVersion,
                                  List<ContextMessage> messages) {}
}
