package com.aiagent.handoff;

public interface HandoffService {
    Ticket create(String userId, String tenantId, String conversationId, String reason);
    record Ticket(String ticketId, String status) {}
}
