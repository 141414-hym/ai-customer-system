package com.aiagent.security;

import com.aiagent.session.SessionKey;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** Obtains the trusted user identity established by Spring Security. */
@Component
public class CurrentUserProvider {

    public String userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken
                || authentication.getName() == null
                || authentication.getName().isBlank()) {
            throw new UnauthenticatedException();
        }
        return authentication.getName();
    }

    public SessionKey sessionKey(String sessionId) {
        return new SessionKey(userId(), sessionId);
    }

    /**
     * Tenant identity is security context data, never a client-supplied request field.
     * The claim is optional for single-tenant development deployments.
     */
    public String tenantId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            String tenantId = jwtAuthentication.getToken().getClaimAsString("tenant_id");
            return tenantId == null ? "" : tenantId.trim();
        }
        return "";
    }

    public static final class UnauthenticatedException extends RuntimeException {
        public UnauthenticatedException() {
            super("未认证请求");
        }
    }
}
