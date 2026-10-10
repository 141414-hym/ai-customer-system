package com.aiagent.config;

import com.aiagent.config.node.*;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.file.FileSystemSaver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;
import java.nio.file.Path;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/**
 * 图配置：仅负责 keyStrategyFactory + 节点注册 + 连线。
 * 各节点逻辑拆分到 config/node/ 下各自的类中。
 */
@Configuration
public class GraphAgentConfiguration {

    private final SemanticRouteNode semanticRouteNode;
    private final ClarifyNode clarifyNode;
    private final FaqNode faqNode;
    private final KnowledgeNode knowledgeNode;
    private final BusinessToolNode businessToolNode;
    private final ExternalSearchNode externalSearchNode;
    private final HumanHandoffNode humanHandoffNode;
    private final AiReplyNode aiReplyNode;
    private final AppProperties appProperties;

    public GraphAgentConfiguration(SemanticRouteNode semanticRouteNode, ClarifyNode clarifyNode,
                                   FaqNode faqNode, KnowledgeNode knowledgeNode,
                                   BusinessToolNode businessToolNode, ExternalSearchNode externalSearchNode,
                                   HumanHandoffNode humanHandoffNode, AiReplyNode aiReplyNode,
                                   AppProperties appProperties) {
        this.semanticRouteNode = semanticRouteNode;
        this.clarifyNode = clarifyNode;
        this.faqNode = faqNode;
        this.knowledgeNode = knowledgeNode;
        this.businessToolNode = businessToolNode;
        this.externalSearchNode = externalSearchNode;
        this.humanHandoffNode = humanHandoffNode;
        this.aiReplyNode = aiReplyNode;
        this.appProperties = appProperties;
    }

    @Bean
    public CompiledGraph customerSupportGraph() throws GraphStateException {
        KeyStrategyFactory keyStrategyFactory = () -> {
            Map<String, KeyStrategy> strategies = new HashMap<>();
            // 输入层
            strategies.put("input", new ReplaceStrategy());
            strategies.put("userId", new ReplaceStrategy());
            strategies.put("sessionId", new ReplaceStrategy());
  strategies.put("tenantId", new ReplaceStrategy());
  strategies.put("productCode", new ReplaceStrategy());
  strategies.put("locale", new ReplaceStrategy());
  strategies.put("messages", new AppendStrategy(false));

            // 历史上下文
            strategies.put("historySummary", new ReplaceStrategy());
            strategies.put("recentHistory", new ReplaceStrategy());
            strategies.put("lastRoute", new ReplaceStrategy());
            strategies.put("lastNeedClarify", new ReplaceStrategy());
            strategies.put("pendingQuestion", new ReplaceStrategy());
            strategies.put("turnIndex", new ReplaceStrategy());
            // 决策层
            strategies.put("route", new ReplaceStrategy());
            strategies.put("confidence", new ReplaceStrategy());
            strategies.put("decisionReason", new ReplaceStrategy());
            strategies.put("status", new ReplaceStrategy());
            // 调试
            strategies.put("codeScores", new ReplaceStrategy());
            strategies.put("llmScores", new ReplaceStrategy());
            strategies.put("finalScores", new ReplaceStrategy());
            strategies.put("trace", new ReplaceStrategy());
            // 输出
            strategies.put("answer", new ReplaceStrategy());
            return strategies;
        };

        StateGraph graph = new StateGraph("customer_support_graph", keyStrategyFactory)
                .addNode("semantic_route", semanticRouteNode.getAction())
                .addNode("clarify", clarifyNode.getAction())
                .addNode("faq", faqNode.getAction())
                .addNode("knowledge_base", knowledgeNode.getAction())
                .addNode("business_tool", businessToolNode.getAction())
                .addNode("external_search", externalSearchNode.getAction())
                .addNode("human_handoff", humanHandoffNode.getAction())
                .addNode("ai_reply", aiReplyNode.getAction())
                .addEdge(START, "semantic_route")
                .addConditionalEdges("semantic_route",
                        state -> java.util.concurrent.CompletableFuture
                                .completedFuture(resolveAfterSemanticRoute(state)),
                        Map.of(
                                "clarify", "clarify",
                                "faq", "faq",
                                "knowledge_base", "knowledge_base",
                                "business_tool", "business_tool",
                                "external_search", "external_search",
                                "human_handoff", "human_handoff"
                        ))
                .addConditionalEdges("faq",
                        state -> java.util.concurrent.CompletableFuture
                                .completedFuture(resolveAfterBusinessNode(state)),
                        Map.of("ai_reply", "ai_reply"))
                .addConditionalEdges("knowledge_base",
                        state -> java.util.concurrent.CompletableFuture
                                .completedFuture(resolveAfterBusinessNode(state)),
                        Map.of("ai_reply", "ai_reply"))
                .addConditionalEdges("business_tool",
                        state -> java.util.concurrent.CompletableFuture
                                .completedFuture(resolveAfterBusinessNode(state)),
                        Map.of("ai_reply", "ai_reply"))
                .addConditionalEdges("external_search",
                        state -> java.util.concurrent.CompletableFuture
                                .completedFuture(resolveAfterBusinessNode(state)),
                        Map.of("ai_reply", "ai_reply"))
                .addEdge("clarify", END)
                .addEdge("human_handoff", END)
                .addEdge("ai_reply", END);

        FileSystemSaver checkpointSaver = FileSystemSaver.builder()
                .targetFolder(Path.of(appProperties.getMemory().getCheckpointDirectory()))
                .stateSerializer(graph.getStateSerializer())
                .build();

        SaverConfig saverConfig = SaverConfig.builder()
                .register(checkpointSaver)
                .build();

        CompileConfig compileConfig = CompileConfig.builder()
                .saverConfig(saverConfig)
                .build();

        return graph.compile(compileConfig);
    }

    private String resolveAfterSemanticRoute(com.alibaba.cloud.ai.graph.OverAllState state) {
        String route = fallback(readValue(state, "route"), "external_search");
        return route;
    }

    private String resolveAfterBusinessNode(com.alibaba.cloud.ai.graph.OverAllState state) {
        String route = fallback(readValue(state, "route"), "ai_reply");
        return route;
    }

    private static String readValue(com.alibaba.cloud.ai.graph.OverAllState state, String key) {
        try {
            Object value = state.value(key).orElse(null);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ex) {
            return "";
        }
    }

    private static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
