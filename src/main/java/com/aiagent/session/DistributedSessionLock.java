package com.aiagent.session;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
public class DistributedSessionLock {
    private static final Duration TTL = Duration.ofSeconds(45);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get',KEYS[1]) == ARGV[1] then return redis.call('del',KEYS[1]) else return 0 end", Long.class);
    private final StringRedisTemplate redis;

    public DistributedSessionLock(StringRedisTemplate redis) { this.redis = redis; }

    public Lease tryAcquire(SessionKey key) {
        String token = UUID.randomUUID().toString();
        String lockKey = "agent:session:lock:" + key.threadId();
        Boolean acquired = redis.opsForValue().setIfAbsent(lockKey, token, TTL);
        return Boolean.TRUE.equals(acquired) ? new Lease(lockKey, token) : null;
    }

    public void release(Lease lease) {
        if (lease != null) redis.execute(RELEASE, List.of(lease.key()), lease.token());
    }

    public record Lease(String key, String token) {}
}
