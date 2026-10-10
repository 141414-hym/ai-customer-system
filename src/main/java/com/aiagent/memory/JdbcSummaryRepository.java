package com.aiagent.memory;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcSummaryRepository implements SummaryRepository {
    private final JdbcTemplate jdbc;

    public JdbcSummaryRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Optional<SummaryRecord> find(String conversationId) {
        return jdbc.query("SELECT conversation_id,summary_json,covered_until_sequence,summary_version,model_name,prompt_version FROM conversation_summary WHERE conversation_id=?",
                rs -> rs.next() ? Optional.of(map(rs)) : Optional.empty(), conversationId);
    }

    @Override
    @Transactional
    public SummaryRecord createIfAbsent(String conversationId) {
        jdbc.update("INSERT IGNORE INTO conversation_summary(conversation_id,summary_json) VALUES (?,?)",
                conversationId, "{}");
        return find(conversationId).orElseThrow();
    }

    @Override
    public boolean updateIfVersion(String conversationId, long expectedVersion, String summaryJson,
                                   long coveredUntilSequence, String modelName, String promptVersion) {
        return jdbc.update("""
                UPDATE conversation_summary
                SET summary_json=?, covered_until_sequence=?, summary_version=summary_version+1,
                    model_name=?, prompt_version=?, updated_at=CURRENT_TIMESTAMP
                WHERE conversation_id=? AND summary_version=?
                """, summaryJson, coveredUntilSequence, modelName, promptVersion,
                conversationId, expectedVersion) == 1;
    }

    private static SummaryRecord map(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new SummaryRecord(rs.getString(1), rs.getString(2), rs.getLong(3),
                rs.getLong(4), rs.getString(5), rs.getString(6));
    }
}
