package com.aiagent.task;

import com.aiagent.dto.ChatEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;

/** In-process worker for long-running chat Graph executions. */
@Service
public class ChatTaskService {
    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();

    public TaskRecord submit(String userId, String sessionId, Supplier<List<ChatEvent>> work) {
        String taskId = UUID.randomUUID().toString();
        TaskRecord accepted = new TaskRecord(taskId, userId, sessionId, Status.QUEUED, List.of(), null, Instant.now());
        tasks.put(taskId, accepted);
        workers.submit(() -> {
            tasks.computeIfPresent(taskId, (id, old) -> old.withStatus(Status.RUNNING));
            try {
                List<ChatEvent> events = work.get();
                tasks.computeIfPresent(taskId, (id, old) -> old.completed(events));
            } catch (RuntimeException ex) {
                tasks.computeIfPresent(taskId, (id, old) -> old.failed("GRAPH_EXECUTION_FAILED"));
            }
        });
        return accepted;
    }

    public TaskRecord get(String taskId, String userId) {
        TaskRecord record = tasks.get(taskId);
        if (record == null || !record.userId().equals(userId)) throw new IllegalArgumentException("任务不存在");
        return record;
    }

    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }

    public record TaskRecord(String taskId, String userId, String sessionId, Status status,
                             List<ChatEvent> events, String errorCode, Instant createdAt) {
        TaskRecord withStatus(Status status) { return new TaskRecord(taskId, userId, sessionId, status, events, errorCode, createdAt); }
        TaskRecord completed(List<ChatEvent> events) { return new TaskRecord(taskId, userId, sessionId, Status.COMPLETED, events, null, createdAt); }
        TaskRecord failed(String errorCode) { return new TaskRecord(taskId, userId, sessionId, Status.FAILED, List.of(), errorCode, createdAt); }
    }
}
