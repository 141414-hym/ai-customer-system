package com.aiagent.persistence;

import java.util.Optional;

public interface ConversationMessageRepository {
    ConversationRecord ensureConversation(String conversationId, String userId,
                                           String tenantId, String threadId);

    MessageRecord appendUserMessage(String conversationId, String clientMessageId,
                                    String runId, String content);

    MessageRecord appendAssistantMessage(String conversationId, String runId, String content,
                                         String status);

    Optional<MessageRecord> findByClientMessageId(String conversationId, String clientMessageId);

    Optional<String> findCompletedAnswer(String conversationId, String clientMessageId);

    record ConversationRecord(String conversationId, String userId, String tenantId,
                               String threadId, long version) {}

    record MessageRecord(String messageId, String conversationId, long sequenceNo,
                         String role, String content, String status, boolean newlyCreated) {}
}
