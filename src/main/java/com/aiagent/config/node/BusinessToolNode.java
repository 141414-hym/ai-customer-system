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
public class BusinessToolNode {

    private static final Logger log = LoggerFactory.getLogger(BusinessToolNode.class);

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            log.info("[business_tool] route={} reason={}",
                    readValue((OverAllState) state, "route"),
                    readValue((OverAllState) state, "decisionReason"));
            return Map.of("answer", "【业务工具框架节点】\n当前问题已进入业务工具分支。",
                    "route", "ai_reply");
        });
    }
}
