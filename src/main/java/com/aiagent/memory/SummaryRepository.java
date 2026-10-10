package com.aiagent.memory;

import java.util.Optional;

public interface SummaryRepository {
    Optional<SummaryRecord> find(String conversationId);
    SummaryRecord createIfAbsent(String conversationId);
    boolean updateIfVersion(String conversationId, long expectedVersion, String summaryJson,
                            long coveredUntilSequence, String modelName, String promptVersion);

    record SummaryRecord(String conversationId, String summaryJson, long coveredUntilSequence,
                         long summaryVersion, String modelName, String promptVersion) {}
}
