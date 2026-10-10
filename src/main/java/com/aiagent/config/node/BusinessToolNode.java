package com.aiagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.aiagent.security.CurrentUserProvider;
import com.aiagent.tool.OrderQueryTool;
import com.aiagent.guard.ToolCallGuard;

import java.util.Map;
import java.util.LinkedHashMap;

import static com.aiagent.config.util.StateUtils.*;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Component
public class BusinessToolNode {

    private static final Logger log = LoggerFactory.getLogger(BusinessToolNode.class);
    private final OrderQueryTool orderQueryTool;
    private final CurrentUserProvider currentUserProvider;
    private final ToolCallGuard toolCallGuard;

    public BusinessToolNode(OrderQueryTool orderQueryTool, CurrentUserProvider currentUserProvider,
                            ToolCallGuard toolCallGuard) {
        this.orderQueryTool = orderQueryTool;
        this.currentUserProvider = currentUserProvider;
        this.toolCallGuard = toolCallGuard;
    }

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            log.info("[business_tool] route={} reason={}",
                    readValue((OverAllState) state, "route"),
                    readValue((OverAllState) state, "decisionReason"));
            String input = readValue((OverAllState) state, "input");
            String runId = readValue((OverAllState) state, "traceId");
            ToolCallGuard.Decision allowed = toolCallGuard.check(runId, "queryOrder", input);
            if (!allowed.allowed()) {
                return Map.of("answer", "本次订单查询已执行过或达到调用上限，请稍后重试。",
                        "toolError", allowed.reason(), "route", "ai_reply");
            }
            OrderQueryTool.ToolResult result = orderQueryTool.execute(currentUserProvider.userId(), input);
            Map<String, Object> update = new LinkedHashMap<>();
            update.put("answer", result.message());
            update.put("toolResult", result);
            update.put("route", "ai_reply");
            return update;
        });
    }
}
