package com.aiagent.handoff;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class JdbcHandoffService implements HandoffService {
    private final JdbcTemplate jdbc;
    public JdbcHandoffService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public Ticket create(String userId, String tenantId, String conversationId, String reason) {
        String ticketId = "HT-" + UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO handoff_ticket(ticket_id,user_id,tenant_id,conversation_id,reason,status)
                VALUES (?,?,?,?,?,'PENDING')
                """, ticketId, userId, tenantId, conversationId, reason);
        return new Ticket(ticketId, "PENDING");
    }
}
