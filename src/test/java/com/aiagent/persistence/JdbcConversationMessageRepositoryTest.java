package com.aiagent.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

@JdbcTest
@Import(JdbcConversationMessageRepository.class)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:aiagent;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.sql.init.mode=always",
        "spring.flyway.enabled=false"
})
class JdbcConversationMessageRepositoryTest {

    @Autowired
    private ConversationMessageRepository repository;

    @Test
    void appendsUserMessageIdempotently() {
        repository.ensureConversation("s-1", "u-1", "tenant-a", "thread-1");

        ConversationMessageRepository.MessageRecord first = repository.appendUserMessage(
                "s-1", "client-1", "run-1", "hello");
        ConversationMessageRepository.MessageRecord retry = repository.appendUserMessage(
                "s-1", "client-1", "run-2", "hello");

        assertEquals(first.messageId(), retry.messageId());
        assertEquals(1L, retry.sequenceNo());
        assertEquals("COMPLETED", retry.status());
        assertEquals(true, first.newlyCreated());
        assertEquals(false, retry.newlyCreated());
    }
}
