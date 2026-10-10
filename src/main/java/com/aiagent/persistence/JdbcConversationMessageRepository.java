package com.aiagent.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcConversationMessageRepository implements ConversationMessageRepository {
    private final JdbcTemplate jdbc;

    public JdbcConversationMessageRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public ConversationRecord ensureConversation(String conversationId, String userId,
                                                  String tenantId, String threadId) {
        try {
            jdbc.update("""
                    INSERT INTO conversation(conversation_id,user_id,tenant_id,thread_id,last_message_at)
                    VALUES (?,?,?,?,?)
                    """, conversationId, userId, tenantId, threadId, Instant.now());
        } catch (DuplicateKeyException ignored) {
            // Idempotent conversation initialization. Ownership is checked by the query below.
        }
        return jdbc.queryForObject("""
                SELECT conversation_id,user_id,tenant_id,thread_id,version
                FROM conversation WHERE conversation_id=? AND user_id=?
                """, (rs, rowNum) -> new ConversationRecord(
                rs.getString("conversation_id"), rs.getString("user_id"),
                rs.getString("tenant_id"), rs.getString("thread_id"), rs.getLong("version")),
                conversationId, userId);
    }

    @Override
    @Transactional
    public MessageRecord appendUserMessage(String conversationId, String clientMessageId,
                                           String runId, String content) {
        Optional<MessageRecord> existing = findByClientMessageId(conversationId, clientMessageId);
        if (existing.isPresent()) {
            MessageRecord record = existing.get();
            return new MessageRecord(record.messageId(), record.conversationId(), record.sequenceNo(),
                    record.role(), record.content(), record.status(), false);
        }

        Long next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no),0)+1 FROM chat_message WHERE conversation_id=?",
                Long.class, conversationId);
        String messageId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO chat_message(message_id,conversation_id,run_id,client_message_id,
                    sequence_no,role,content,status,completed_at)
                VALUES (?,?,?,?,?,'USER',?,'COMPLETED',CURRENT_TIMESTAMP)
                """, messageId, conversationId, runId, clientMessageId, next, content);
        jdbc.update("UPDATE conversation SET last_message_at=CURRENT_TIMESTAMP,version=version+1 WHERE conversation_id=?",
                conversationId);
        return new MessageRecord(messageId, conversationId, next, "USER", content, "COMPLETED", true);
    }

    @Override
    @Transactional
    public MessageRecord appendAssistantMessage(String conversationId, String runId,
                                                String content, String status) {
        Long next = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sequence_no),0)+1 FROM chat_message WHERE conversation_id=?",
                Long.class, conversationId);
        String messageId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO chat_message(message_id,conversation_id,run_id,sequence_no,role,content,status,completed_at)
                VALUES (?,?,?,?, 'ASSISTANT',?,?,CASE WHEN ?='COMPLETED' THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, messageId, conversationId, runId, next, content, status, status);
        jdbc.update("UPDATE conversation SET last_message_at=CURRENT_TIMESTAMP,version=version+1 WHERE conversation_id=?",
                conversationId);
        return new MessageRecord(messageId, conversationId, next, "ASSISTANT", content, status, true);
    }

    @Override
    public Optional<MessageRecord> findByClientMessageId(String conversationId, String clientMessageId) {
        return jdbc.query("""
                SELECT message_id,conversation_id,sequence_no,role,content,status
                FROM chat_message WHERE conversation_id=? AND client_message_id=?
                """, rs -> rs.next() ? Optional.of(mapMessage(rs)) : Optional.empty(),
                conversationId, clientMessageId);
    }

    @Override
    public Optional<String> findCompletedAnswer(String conversationId, String clientMessageId) {
        return jdbc.query("""
                SELECT assistant.content FROM chat_message user_message
                JOIN chat_message assistant ON assistant.run_id=user_message.run_id
                WHERE user_message.conversation_id=? AND user_message.client_message_id=?
                  AND assistant.role='ASSISTANT' AND assistant.status='COMPLETED'
                ORDER BY assistant.sequence_no DESC LIMIT 1
                """, rs -> rs.next() ? Optional.of(rs.getString("content")) : Optional.empty(),
                conversationId, clientMessageId);
    }

    private static MessageRecord mapMessage(ResultSet rs) throws SQLException {
        return new MessageRecord(rs.getString("message_id"), rs.getString("conversation_id"),
                rs.getLong("sequence_no"), rs.getString("role"), rs.getString("content"),
                rs.getString("status"), true);
    }
}
