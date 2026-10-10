package com.aiagent.config.node;

import com.aiagent.dto.EntryContext;
import com.aiagent.dto.EntryDecision;
import com.aiagent.config.util.StateUtils;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import com.aiagent.config.model.ModelRegistry;
import com.aiagent.config.model.ModelRole;
import com.aiagent.guard.ModelCallBudget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;
import static com.aiagent.config.util.StateUtils.*;

/**
 * 语义路由节点：代码规则打分 + LLM 意图识别 + 双路融合，输出最终路由决策。
 */
@Component
public class SemanticRouteNode {

    private static final Logger log = LoggerFactory.getLogger(SemanticRouteNode.class);

    private final ModelRegistry modelRegistry;
    private final ModelCallBudget modelCallBudget;
    private final ObjectMapper objectMapper;

    public SemanticRouteNode(ModelRegistry modelRegistry, ModelCallBudget modelCallBudget,
                             ObjectMapper objectMapper) {
        this.modelRegistry = modelRegistry;
        this.modelCallBudget = modelCallBudget;
        this.objectMapper = objectMapper;
    }

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            EntryContext context = buildEntryContext((OverAllState) state);
            log.info("[semantic_route] context={}", context);

            Map<String, Double> codeScores = scoreByCode(context);
            log.info("[semantic_route] code_scores={}", codeScores);
            EntryDecision codeDecision = buildDecisionFromScores(codeScores, "代码规则已明确识别入口决策", "code");
            log.info("[semantic_route] code_decision={}", codeDecision);

            EntryDecision llmDecision = analyzeByModel(context, codeDecision);
            Map<String, Double> llmScores = scoreByDecision(llmDecision);
            log.info("[semantic_route] llm_decision={}", llmDecision);
            log.info("[semantic_route] llm_scores={}", llmScores);

            Map<String, Double> finalScores = mergeScores(codeScores, llmScores);
            log.info("[semantic_route] final_scores={}", finalScores);
            EntryDecision finalDecision = buildMergedDecision(codeDecision, llmDecision, finalScores);
            log.info("[semantic_route] final_decision={}", finalDecision);
            log.info("[semantic_route] score_flow={{codeRoute={}, codeConfidence={}, llmRoute={}, llmConfidence={}, finalRoute={}, finalConfidence={}}}",
                    codeDecision.route(), codeDecision.confidence(),
                    llmDecision.route(), llmDecision.confidence(),
                    finalDecision.route(), finalDecision.confidence());

            Map<String, Object> update = new HashMap<>();
            update.put("route", finalDecision.route());
            update.put("intentRoute", finalDecision.route());
            update.put("nextAction", finalDecision.route());
            update.put("lastRoute", finalDecision.route());
            update.put("confidence", finalDecision.confidence());
            update.put("decisionReason", finalDecision.decisionReason());
            update.put("status", 200);
            update.put("codeScores", codeScores);
            update.put("llmScores", llmScores);
            update.put("finalScores", finalScores);
            update.put("trace", finalDecision.trace());
            return update;
        });
    }

    // ========== 代码规则打分 ==========

    private Map<String, Double> scoreByCode(EntryContext context) {
        String normalized = normalize(context.contextText());
        Map<String, Double> scores = baseScores();

        if (containsAny(normalized, "人工", "投诉", "转接", "客服", "人工客服", "找人工")) {
            scores.put("human_handoff", scores.get("human_handoff") + 100d);
        }
        if (containsAny(normalized, "订单", "物流", "发货", "快递", "工单", "查单", "退款状态")) {
            scores.put("business_tool", scores.get("business_tool") + 90d);
        }
        if (containsAny(normalized, "怎么", "如何", "规则", "政策", "流程", "售后", "说明")) {
            scores.put("knowledge_base", scores.get("knowledge_base") + 80d);
        }
        if (containsAny(normalized, "官网", "最新", "外部", "公告", "通知")) {
            scores.put("external_search", scores.get("external_search") + 80d);
        }
        if (containsAny(normalized, "退款", "发票", "账号", "密码", "登录", "改密", "忘记密码")) {
            scores.put("faq", scores.get("faq") + 50d);
        }
        if (containsAny(normalized, "这个", "怎么处理", "怎么办", "怎么弄", "能不能", "帮我看", "帮我确认", "不太明白", "有点异常", "这是什么情况")) {
            scores.put("clarify", scores.get("clarify") + 80d);
        }

        Matcher orderMatcher = Pattern.compile("\\b\\d{6,}\\b").matcher(normalized);
        if (orderMatcher.find()) {
            scores.put("business_tool", scores.get("business_tool") + 60d);
        }

        if (context.lastNeedClarify() || !context.pendingQuestion().isBlank()) {
            scores.put("clarify", scores.get("clarify") + 60d);
        }
        if ("business_tool".equals(context.lastRoute()) && containsAny(normalized, "查", "看", "确认", "状态", "进度")) {
            scores.put("business_tool", scores.get("business_tool") + 50d);
        }
        return scores;
    }

    private EntryDecision buildDecisionFromScores(Map<String, Double> scores, String defaultReason, String source) {
        String route = maxKey(scores);
        double rawScore = scores.getOrDefault(route, 0d);
        double confidence = normalizeScore(rawScore);
        Map<String, Object> trace = new HashMap<>();
        trace.put(source + "Scores", scores);
        return new EntryDecision(route, confidence, defaultReason, trace);
    }

    // ========== LLM 打分 ==========

    private EntryDecision analyzeByModel(EntryContext context, EntryDecision codeDecision) {
        String traceId = context.sessionId() + ":" + context.turnIndex();
        if (!modelCallBudget.tryAcquire(traceId)) return codeDecision;
        ChatClient client = modelRegistry.client(ModelRole.ROUTE);
        String prompt = """
                # 角色定位
                你是客服系统的意图路由器。你的职责是根据用户输入判断应该走哪个处理分支。
                你不需要回答问题，只需要判断意图。

                # 能力边界

                本地知识库已包含以下内容：产品价格、车型参数、配置信息、售后政策、保修条款、优惠活动、购车流程、FAQ 常见问答。
                当用户询问上述相关内容时，默认优先走 knowledge_base 或 faq。

                各路由的适用场景与排除条件：

                - knowledge_base：需检索本地知识库。适用于产品询价、车型参数、配置对比、售后政策、保修条款、购车流程等已有文档覆盖的问题。这是最常用的路由。
                - faq：常见简单问题，可直接回答无需深度检索。适用于退款规则、发票、账号登录、密码找回等标准问答。
                - business_tool：需查询实时业务系统。仅限已有具体订单号/工单号/物流单号的查询。没有订单号的询价不走这里。
                - external_search：需搜索外部最新信息。仅限本地知识库明确不存在的内容，如行业新闻、竞品动态、官网最新公告。本地已有的产品信息不走这里。
                - human_handoff：需转人工。适用于用户明确要求转人工、投诉、涉及安全风险或法律合规问题。
                - clarify：信息不足，无法判断意图。适用于单字输入、纯表情、无意义闲聊等无法确定用户诉求的情况。

                # 样例

                用户："G6多少钱" → knowledge_base
                用户："这款车什么配置" → knowledge_base
                用户："G6和G9哪个更划算" → knowledge_base
                用户："怎么申请售后保修" → knowledge_base
                用户："现在有什么优惠活动" → knowledge_base
                用户："退款怎么操作" → faq
                用户："发票怎么开" → faq
                用户："我的订单 ORD123456 发货了吗" → business_tool
                用户："物流单号 SF123456 到哪了" → business_tool
                用户："我要投诉" → human_handoff
                用户："转人工" → human_handoff
                用户："你好" → clarify
                用户："在吗" → clarify

                # 历史上下文
                更早对话摘要：%s
                最近对话：%s
                上一轮路由：%s
                上一轮是否补问：%s
                待补问题：%s
                当前轮次：%d

                # 用户输入
                %s

                # 输出格式
                只输出一行 JSON，不要任何其他文字：
                {"route":"选中的路由","confidence":"相应分数","decisionReason":"简短理由"}
                输出分数为百分制
                """.formatted(
                context.historySummary(), context.recentHistory(),
                context.lastRoute(), context.lastNeedClarify(), context.pendingQuestion(),
                context.turnIndex(),
                context.inputText());
        String content = client.prompt().user(prompt).call().content();
        return parseModelResult(content, codeDecision);
    }

    private EntryDecision parseModelResult(String content, EntryDecision fallbackDecision) {
        if (content == null || content.isBlank()) {
            return fallbackDecision;
        }
        String route = fallbackDecision.route();
        double confidence = fallbackDecision.confidence();
        String decisionReason = fallbackDecision.decisionReason();
        try {
            JsonNode node = objectMapper.readTree(content);
            route = parseRoute(node.path("route").asText(), route);
            double rawConfidence = node.path("confidence").asDouble(confidence);
            confidence = rawConfidence > 1.0d ? normalizeScore(rawConfidence) : rawConfidence;
            confidence = Math.max(0d, Math.min(1d, confidence));
            decisionReason = valueOrDefault(node.path("decisionReason").asText(), decisionReason);
        } catch (Exception ex) {
            log.warn("[semantic_route] invalid structured model output, fallback to rules");
            return fallbackDecision;
        }

        Map<String, Object> trace = new HashMap<>();
        trace.put("modelRawContent", content);
        trace.put("modelRoute", route);

        return new EntryDecision(route, confidence, decisionReason, trace);
    }

    private Map<String, Double> scoreByDecision(EntryDecision decision) {
        Map<String, Double> scores = baseScores();
        if (decision == null) {
            return scores;
        }
        double scaled = decision.confidence() * 100d;
        scores.put(decision.route(), Math.max(scores.get(decision.route()), scaled));
        return scores;
    }

    // ========== 融合 ==========

    private Map<String, Double> mergeScores(Map<String, Double> codeScores, Map<String, Double> llmScores) {
        double codeWeight = 0.4d;
        double llmWeight = 0.6d;
        Map<String, Double> finalScores = new HashMap<>();
        for (String route : ROUTES) {
            double codeScore = codeScores.getOrDefault(route, 0d);
            double llmScore = llmScores.getOrDefault(route, 0d);
            finalScores.put(route, codeWeight * codeScore + llmWeight * llmScore);
        }
        return finalScores;
    }

    private EntryDecision buildMergedDecision(EntryDecision codeDecision,
                                              EntryDecision llmDecision,
                                              Map<String, Double> finalScores) {
        String route = maxKey(finalScores);
        double confidence = normalizeScore(finalScores.getOrDefault(route, 0d));
        String selectedRoute = route;
        double secondScore = finalScores.entrySet().stream()
                .filter(entry -> !entry.getKey().equals(selectedRoute))
                .mapToDouble(Map.Entry::getValue).max().orElse(0d);
        if (finalScores.getOrDefault(route, 0d) < 25d || finalScores.getOrDefault(route, 0d) - secondScore < 10d) {
            route = "clarify";
            confidence = normalizeScore(finalScores.getOrDefault(route, 0d));
        }
        String reason = "融合代码(0.4)与模型(0.6)后得到最终决策";

        Map<String, Object> trace = new HashMap<>(llmDecision.trace());
        trace.put("codeRoute", codeDecision.route());
        trace.put("codeConfidence", codeDecision.confidence());
        trace.put("finalScores", finalScores);
        trace.put("selectedRoute", route);

        return new EntryDecision(route, confidence, reason, trace);
    }
}
