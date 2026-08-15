package com.aiagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.aiagent.config.util.StateUtils.*;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Component
public class HumanHandoffNode {

    private static final Logger log = LoggerFactory.getLogger(HumanHandoffNode.class);

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            String reason = fallback(readValue((OverAllState) state, "decisionReason"), "需要人工介入");
            log.info("[human_handoff] reason={}", reason);
            return Map.of("answer", reason);
        });
    }
}
