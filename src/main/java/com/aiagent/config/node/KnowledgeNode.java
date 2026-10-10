package com.aiagent.config.node;

import com.aiagent.config.pipeline.QueryCleaner;
import com.aiagent.config.pipeline.QueryRewriter;
import com.aiagent.config.pipeline.Retriever;
import com.aiagent.config.pipeline.EvidenceGate;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aiagent.config.model.ModelRegistry;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.aiagent.config.util.StateUtils.*;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Component
public class KnowledgeNode {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeNode.class);

    private final QueryCleaner queryCleaner;
    private final QueryRewriter queryRewriter;
    private final Retriever retriever;
    private final EvidenceGate evidenceGate;

    public KnowledgeNode(ModelRegistry modelRegistry, QueryCleaner queryCleaner,
                         QueryRewriter queryRewriter, Retriever retriever, EvidenceGate evidenceGate) {
        this.queryCleaner = queryCleaner;
        this.queryRewriter = queryRewriter;
        this.retriever = retriever;
        this.evidenceGate = evidenceGate;
    }

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            String input = readValue((OverAllState) state, "input");
            String route = readValue((OverAllState) state, "route");
            String historySummary = readValue((OverAllState) state, "historySummary");
            String recentHistory = readValue((OverAllState) state, "recentHistory");
            String tenantId = readValue((OverAllState) state, "tenantId");
            String productCode = readValue((OverAllState) state, "productCode");
            String locale = readValue((OverAllState) state, "locale");
            log.info("[knowledge_base] route={} input={}", route, input);

            String cleaned = queryCleaner.cleanQuery(input);
            List<String> queries = queryRewriter.rewriteQuery(cleaned, historySummary, recentHistory);
            String retrieved = retriever.retrieveMultiQuery(queries, tenantId, productCode, locale);
            EvidenceGate.EvidenceDecision evidence = evidenceGate.assess(retrieved);
            log.info("[knowledge_base] retrieved {} chars", retrieved.length());

            return Map.of("answer", retrieved, "subQueries", String.join("|||", queries),
                    "evidenceSufficient", evidence.sufficient(), "evidenceCount", evidence.count(),
                    "evidenceReason", evidence.reason(), "citations", retriever.citationsFrom(retrieved),
                    "route", "ai_reply");
        });
    }
}
