package com.aiagent.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SummaryTrigger {
    private static final int WINDOW_LIMIT = 6;
    private final RedisContextStore contextStore;
    private final SummaryService summaryService;
    private final ObjectMapper mapper;

    public SummaryTrigger(RedisContextStore contextStore, SummaryService summaryService, ObjectMapper mapper) {
        this.contextStore = contextStore; this.summaryService = summaryService; this.mapper = mapper;
    }

    public void maybeCompress(String userId, String conversationId) {
        RedisContextStore.ContextSnapshot snapshot = contextStore.load(userId, conversationId);
        if (snapshot.messages().size() <= WINDOW_LIMIT) return;
        long covered = snapshot.messages().get(snapshot.messages().size() - WINDOW_LIMIT - 1).sequenceNo();
        String summary;
        try {
            summary = mapper.writeValueAsString(Map.of(
                    "type", "structured-conversation-summary",
                    "facts", snapshot.messages().stream().limit(snapshot.messages().size() - WINDOW_LIMIT).toList()));
        } catch (JsonProcessingException e) {
            return;
        }
        summaryService.persist(userId, conversationId, summary, covered, "deterministic-v1", "summary-v1");
    }
}
