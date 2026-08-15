package com.aiagent.memory;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();

    public SessionState getOrCreate(String sessionId) {
        return sessions.computeIfAbsent(sessionId, SessionState::new);
    }

    public void updateLastMessage(String sessionId, String message) {
        getOrCreate(sessionId).setLastMessage(message);
    }

    public SessionState snapshot(String sessionId) {
        return sessions.get(sessionId);
    }

    public static class SessionState {
        private final String sessionId;
        private final Instant createdAt = Instant.now();
        private volatile String lastMessage;
        private volatile String route;
        private volatile String tenantId;
        private volatile String productCode;

        public SessionState(String sessionId) {
            this.sessionId = sessionId;
        }

        public String getSessionId() {
            return sessionId;
        }

        public Instant getCreatedAt() {
            return createdAt;
        }

        public String getLastMessage() {
            return lastMessage;
        }

        public void setLastMessage(String lastMessage) {
            this.lastMessage = lastMessage;
        }

        public String getRoute() {
            return route;
        }

        public void setRoute(String route) {
            this.route = route;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getProductCode() {
            return productCode;
        }

        public void setProductCode(String productCode) {
            this.productCode = productCode;
        }
    }
}
