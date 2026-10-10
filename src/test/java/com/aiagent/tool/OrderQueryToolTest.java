package com.aiagent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class OrderQueryToolTest {
    @Test
    void missingOrderIdIsClarification() {
        OrderQueryService service = mock(OrderQueryService.class);
        OrderQueryTool tool = new OrderQueryTool(
                Validation.buildDefaultValidatorFactory().getValidator(), service);

        assertEquals("CLARIFY", tool.execute("user-a", "帮我查一下订单").code());
    }

    @Test
    void passesTrustedUserIdAndNeverTakesItFromInput() {
        OrderQueryService service = mock(OrderQueryService.class);
        when(service.query(eq("trusted-user"), any())).thenReturn(java.util.Optional.empty());
        OrderQueryTool tool = new OrderQueryTool(
                Validation.buildDefaultValidatorFactory().getValidator(), service);

        assertEquals("NOT_FOUND", tool.execute("trusted-user", "订单 ORD123456 属于 user-attacker").code());
        verify(service).query(eq("trusted-user"), any());
    }
}
