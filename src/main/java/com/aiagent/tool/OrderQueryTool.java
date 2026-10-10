package com.aiagent.tool;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class OrderQueryTool {
    private static final Pattern ORDER_PATTERN = Pattern.compile("\\b(?:XP|ORD)\\d{6,}\\b");
    private final Validator validator;
    private final OrderQueryService orders;

    public OrderQueryTool(Validator validator, OrderQueryService orders) {
        this.validator = validator;
        this.orders = orders;
    }

    public ToolResult execute(String trustedUserId, String rawInput) {
        Matcher matcher = ORDER_PATTERN.matcher(rawInput == null ? "" : rawInput.toUpperCase());
        if (!matcher.find()) return ToolResult.clarify("请提供有效订单号，例如 ORD123456");
        OrderQueryRequest request = new OrderQueryRequest(matcher.group());
        Set<ConstraintViolation<OrderQueryRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) return ToolResult.invalid("订单号格式不正确");
        return orders.query(trustedUserId, request)
                .map(result -> ToolResult.success(result))
                .orElseGet(() -> ToolResult.notFound("未找到订单或无权查看"));
    }

    public record ToolResult(String code, String message, OrderQueryResult data) {
        static ToolResult success(OrderQueryResult data) { return new ToolResult("OK", "查询成功", data); }
        static ToolResult clarify(String message) { return new ToolResult("CLARIFY", message, null); }
        static ToolResult invalid(String message) { return new ToolResult("INVALID_ARGUMENT", message, null); }
        static ToolResult notFound(String message) { return new ToolResult("NOT_FOUND", message, null); }
    }
}
