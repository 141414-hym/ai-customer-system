package com.aiagent.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aiagent.session.SessionKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsUserIdFromAuthenticatedSecurityContext() {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(
                        "user-42", "ignored", AuthorityUtils.NO_AUTHORITIES));

        SessionKey key = provider.sessionKey("session-7");

        assertEquals("user-42", key.userId());
        assertEquals("session-7", key.sessionId());
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThrows(CurrentUserProvider.UnauthenticatedException.class,
                () -> provider.sessionKey("session-7"));
    }

    @Test
    void rejectsAnonymousAuthentication() {
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken(
                        "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThrows(CurrentUserProvider.UnauthenticatedException.class,
                () -> provider.sessionKey("session-7"));
    }
}
