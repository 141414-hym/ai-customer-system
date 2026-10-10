package com.aiagent.config.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import com.aiagent.search.ExternalSearchService;

import java.util.Map;

import static com.aiagent.config.util.StateUtils.*;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Component
public class ExternalSearchNode {

    private static final Logger log = LoggerFactory.getLogger(ExternalSearchNode.class);
    private final ExternalSearchService searchService;

    public ExternalSearchNode(ExternalSearchService searchService) { this.searchService = searchService; }

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            log.info("[external_search] route={} reason={}",
                    readValue((OverAllState) state, "route"),
                    readValue((OverAllState) state, "decisionReason"));
            ExternalSearchService.SearchResult result = searchService.search(
                    readValue((OverAllState) state, "input"), readValue((OverAllState) state, "locale"));
            return Map.of("answer", result.summary(), "searchResult", result, "route", "ai_reply");
        });
    }
}
