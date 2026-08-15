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
public class ClarifyNode {

    private static final Logger log = LoggerFactory.getLogger(ClarifyNode.class);

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            log.info("[clarify] reason={}", readValue((OverAllState) state, "decisionReason"));
            return Map.of("answer", "请补充一下你的问题细节。");
        });
    }
}
