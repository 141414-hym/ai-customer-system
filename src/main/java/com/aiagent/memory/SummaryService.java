package com.aiagent.memory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SummaryService {
    private final SummaryRepository summaries;
    private final RedisContextStore contextStore;

    public SummaryService(SummaryRepository summaries, RedisContextStore contextStore) {
        this.summaries = summaries;
        this.contextStore = contextStore;
    }

    @Transactional
    public boolean persist(String userId, String conversationId, String summaryJson,
                           long coveredUntilSequence, String modelName, String promptVersion) {
        SummaryRepository.SummaryRecord current = summaries.createIfAbsent(conversationId);
        long nextVersion = current.summaryVersion() + 1;
        if (!summaries.updateIfVersion(conversationId, current.summaryVersion(), summaryJson,
                coveredUntilSequence, modelName, promptVersion)) {
            return false;
        }
        return contextStore.updateSummaryIfVersion(userId, conversationId,
                current.summaryVersion(), summaryJson, nextVersion, coveredUntilSequence);
    }
}
