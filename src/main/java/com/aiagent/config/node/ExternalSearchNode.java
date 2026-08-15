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
public class ExternalSearchNode {

    private static final Logger log = LoggerFactory.getLogger(ExternalSearchNode.class);

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            log.info("[external_search] route={} reason={}",
                    readValue((OverAllState) state, "route"),
                    readValue((OverAllState) state, "decisionReason"));
            return Map.of("answer", "【外部搜索框架节点】\n当前问题已进入外部搜索分支。",
                    "route", "ai_reply");
        });
    }
}
