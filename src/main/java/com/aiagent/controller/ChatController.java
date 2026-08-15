package com.aiagent.controller;

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat")
public class ChatController {

    private final CompiledGraph customerSupportGraph;

    public ChatController(CompiledGraph customerSupportGraph) {
        this.customerSupportGraph = customerSupportGraph;
    }

    @PostMapping(value = "/ask", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> ask(@RequestBody Map<String, Object> request) {
        String sessionId = value(request.get("sessionId"), "session-default");
        Map<String, Object> input = Map.of(
                "input", value(request.get("message"), ""),
                "sessionId", sessionId,
                "tenantId", value(request.get("tenantId"), ""),
                "productCode", value(request.get("productCode"), ""),
                "locale", value(request.get("locale"), "zh-CN"),
                "messages", List.of(Map.of("role", "user", "content", value(request.get("message"), "")))
        );
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
        List<Map<String, Object>> events = customerSupportGraph.stream(input, config)
                .map(this::toEvent)
                .collectList()
                .block();
        return Map.of("events", events == null ? List.of() : events);
    }

    @PostMapping(value = "/ask/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<Map<String, Object>> askStream(@RequestBody Map<String, Object> request) {
        String sessionId = value(request.get("sessionId"), "session-default");
        Map<String, Object> input = Map.of(
                "input", value(request.get("message"), ""),
                "sessionId", sessionId,
                "tenantId", value(request.get("tenantId"), ""),
                "productCode", value(request.get("productCode"), ""),
                "locale", value(request.get("locale"), "zh-CN"),
                "messages", List.of(Map.of("role", "user", "content", value(request.get("message"), "")))
        );
        RunnableConfig config = RunnableConfig.builder().threadId(sessionId).build();
        return customerSupportGraph.stream(input, config)
                .map(this::toEvent);
    }

    private Map<String, Object> toEvent(NodeOutput event) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("node", event.node());
        result.put("state", new LinkedHashMap<>(event.state().data()));
        return result;
    }

    private static String value(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }
}
