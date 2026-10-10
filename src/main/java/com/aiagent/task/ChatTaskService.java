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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** In-process worker for long-running chat Graph executions. */
@Service
public class ChatTaskService {
    private final ExecutorService workers = Executors.newFixedThreadPool(8);
    private final Map<String, TaskRecord> tasks = new ConcurrentHashMap<>();
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public ChatTaskService(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public TaskRecord submit(String userId, String sessionId, Supplier<List<ChatEvent>> work) {
        String taskId = UUID.randomUUID().toString();
        TaskRecord accepted = new TaskRecord(taskId, userId, sessionId, Status.QUEUED, List.of(), null, Instant.now());
        tasks.put(taskId, accepted);
        jdbc.update("INSERT INTO agent_task(task_id,user_id,session_id,status) VALUES (?,?,?,?)",
                taskId, userId, sessionId, Status.QUEUED.name());
        workers.submit(() -> {
            tasks.computeIfPresent(taskId, (id, old) -> old.withStatus(Status.RUNNING));
            updateStatus(taskId, Status.RUNNING, null, null);
            try {
                List<ChatEvent> events = work.get();
                tasks.computeIfPresent(taskId, (id, old) -> old.completed(events));
                updateStatus(taskId, Status.COMPLETED, writeEvents(events), null);
            } catch (RuntimeException ex) {
                tasks.computeIfPresent(taskId, (id, old) -> old.failed("GRAPH_EXECUTION_FAILED"));
                updateStatus(taskId, Status.FAILED, null, "GRAPH_EXECUTION_FAILED");
            }
        });
        return accepted;
    }

    public TaskRecord get(String taskId, String userId) {
        TaskRecord record = jdbc.query("""
                SELECT task_id,user_id,session_id,status,result_json,error_code,created_at
                FROM agent_task WHERE task_id=? AND user_id=?
                """, rs -> rs.next() ? new TaskRecord(rs.getString("task_id"), rs.getString("user_id"),
                rs.getString("session_id"), Status.valueOf(rs.getString("status")), readEvents(rs.getString("result_json")),
                rs.getString("error_code"), rs.getTimestamp("created_at").toInstant()) : null, taskId, userId);
        if (record == null) throw new IllegalArgumentException("任务不存在");
        return record;
    }

    private void updateStatus(String taskId, Status status, String resultJson, String errorCode) {
        jdbc.update("UPDATE agent_task SET status=?,result_json=?,error_code=?,updated_at=CURRENT_TIMESTAMP WHERE task_id=?",
                status.name(), resultJson, errorCode, taskId);
    }
    private String writeEvents(List<ChatEvent> events) {
        try { return objectMapper.writeValueAsString(events); }
        catch (Exception ex) { throw new IllegalStateException("无法保存任务结果", ex); }
    }
    private List<ChatEvent> readEvents(String json) {
        if (json == null || json.isBlank()) return List.of();
        try { return objectMapper.readValue(json, new TypeReference<List<ChatEvent>>() {}); }
        catch (Exception ex) { return List.of(); }
    }

    public enum Status { QUEUED, RUNNING, COMPLETED, FAILED }

    public record TaskRecord(String taskId, String userId, String sessionId, Status status,
                             List<ChatEvent> events, String errorCode, Instant createdAt) {
        TaskRecord withStatus(Status status) { return new TaskRecord(taskId, userId, sessionId, status, events, errorCode, createdAt); }
        TaskRecord completed(List<ChatEvent> events) { return new TaskRecord(taskId, userId, sessionId, Status.COMPLETED, events, null, createdAt); }
        TaskRecord failed(String errorCode) { return new TaskRecord(taskId, userId, sessionId, Status.FAILED, List.of(), errorCode, createdAt); }
    }
}
