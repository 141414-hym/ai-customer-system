package com.aiagent.config.node;

import com.aiagent.config.pipeline.QueryCleaner;
import com.aiagent.config.pipeline.QueryRewriter;
import com.aiagent.config.pipeline.Retriever;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.aiagent.config.util.StateUtils.*;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Component
public class KnowledgeNode {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeNode.class);

    private final ChatModel chatModel;
    private final QueryCleaner queryCleaner;
    private final QueryRewriter queryRewriter;
    private final Retriever retriever;

    public KnowledgeNode(ChatModel chatModel, QueryCleaner queryCleaner,
                         QueryRewriter queryRewriter, Retriever retriever) {
        this.chatModel = chatModel;
        this.queryCleaner = queryCleaner;
        this.queryRewriter = queryRewriter;
        this.retriever = retriever;
    }

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            String input = readValue((OverAllState) state, "input");
            String route = readValue((OverAllState) state, "route");
            String historySummary = readValue((OverAllState) state, "historySummary");
            String recentHistory = readValue((OverAllState) state, "recentHistory");
            log.info("[knowledge_base] route={} input={}", route, input);

            String cleaned = queryCleaner.cleanQuery(input);
            List<String> queries = queryRewriter.rewriteQuery(chatModel, cleaned, historySummary, recentHistory);
            String retrieved = retriever.retrieveMultiQuery(queries);
            log.info("[knowledge_base] retrieved {} chars", retrieved.length());

            return Map.of("answer", retrieved, "subQueries", String.join("|||", queries), "route", "ai_reply");
        });
    }
}
