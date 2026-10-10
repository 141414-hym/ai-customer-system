package com.aiagent.config.node;

import com.aiagent.config.pipeline.HistoryManager;
import com.aiagent.guard.AnswerGuard;
import com.aiagent.guard.ModelCallBudget;
import com.aiagent.config.model.ModelRegistry;
import com.aiagent.config.model.ModelRole;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.aiagent.config.util.StateUtils.*;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

@Component
public class AiReplyNode {

    private static final Logger log = LoggerFactory.getLogger(AiReplyNode.class);

    private final ModelRegistry modelRegistry;
    private final HistoryManager historyManager;
    private final AnswerGuard answerGuard;
    private final ModelCallBudget modelCallBudget;

    public AiReplyNode(ModelRegistry modelRegistry, HistoryManager historyManager, AnswerGuard answerGuard,
                       ModelCallBudget modelCallBudget) {
        this.modelRegistry = modelRegistry;
        this.historyManager = historyManager;
        this.answerGuard = answerGuard;
        this.modelCallBudget = modelCallBudget;
    }

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            String input = readValue((OverAllState) state, "input");
            String sessionId = readValue((OverAllState) state, "sessionId");
            String answer = readValue((OverAllState) state, "answer");
            String subQueriesRaw = readValue((OverAllState) state, "subQueries");
            String historySummary = readValue((OverAllState) state, "historySummary");
            String recentHistory = readValue((OverAllState) state, "recentHistory");
            String traceId = readValue((OverAllState) state, "traceId");
            boolean evidenceSufficient = readBoolean((OverAllState) state, "evidenceSufficient");
            int turnIndex = readInt((OverAllState) state, "turnIndex", 0);
            log.info("[ai_reply] input={} answer={} turnIndex={}", input, answer, turnIndex);

            if (!evidenceSufficient && "knowledge_base".equals(readValue((OverAllState) state, "route"))) {
                return Map.of("answer", "抱歉，我暂时没有找到足够可靠的知识依据来回答这个问题。请补充更多信息，或转人工客服协助。",
                        "answerGuard", "REFUSE_INSUFFICIENT_EVIDENCE");
            }

            // 构建历史上下文
            long nodeStart = System.nanoTime();
            StringBuilder historyContext = new StringBuilder();
            if (!historySummary.isBlank()) {
                historyContext.append("更早对话摘要：\n").append(historySummary).append("\n\n");
            }
            if (!recentHistory.isBlank()) {
                historyContext.append("最近对话：\n").append(recentHistory).append("\n\n");
            }

            StringBuilder promptBuilder = new StringBuilder();
            promptBuilder.append("你是客服助手，请根据以下信息生成最终回复给用户。\n\n");
            promptBuilder.append(historyContext);
            promptBuilder.append("用户问题：").append(input).append("\n\n");
            promptBuilder.append("系统查询结果：").append(answer).append("\n\n");

            // 如果有子问题列表，要求逐一覆盖
            if (subQueriesRaw != null && !subQueriesRaw.isBlank() && !subQueriesRaw.equals("null")) {
                String[] subQueries = subQueriesRaw.split("\\|\\|\\|");
                if (subQueries.length > 1) {
                    promptBuilder.append("用户的问题包含以下多个子问题，请对每个子问题逐一回答，不要遗漏任何一个：\n");
                    for (int i = 0; i < subQueries.length; i++) {
                        promptBuilder.append(i + 1).append(". ").append(subQueries[i]).append("\n");
                    }
                    promptBuilder.append("\n");
                }
            }

            promptBuilder.append("请用自然语言整理上述信息，简洁友好地回复用户。");
            long promptBuildMs = elapsedMs(nodeStart);

            if (!modelCallBudget.tryAcquire(sessionId + ":" + turnIndex)) {
                return Map.of("answer", "当前请求已达到模型调用预算，请稍后重试或转人工客服。",
                        "answerGuard", "BUDGET_EXCEEDED");
            }

            String prompt = promptBuilder.toString();
            long modelStart = System.nanoTime();
            ChatClient client = modelRegistry.client(ModelRole.ANSWER);
            String reply;
            try {
                reply = client.prompt().user(prompt).call().content();
            } catch (RuntimeException ex) {
                log.error("[ai_reply_timing] traceId={} stage=model_call_failed modelRole=ANSWER promptChars={} elapsedMs={} error={}",
                        traceId, prompt.length(), elapsedMs(modelStart), ex.toString());
                throw ex;
            }
            long modelCallMs = elapsedMs(modelStart);
            long guardStart = System.nanoTime();
            AnswerGuard.Decision guard = answerGuard.validate(reply);
            long guardMs = elapsedMs(guardStart);
            if (!guard.allowed()) {
                return Map.of("answer", "抱歉，当前回答未通过安全校验，请转人工客服协助。",
                        "answerGuard", "REJECTED_" + guard.reason());
            }

            // 历史管理：最近3轮原文 + 更早的摘要
            String updatedRecent = recentHistory.isBlank()
                    ? "用户: " + input + "\n助手: " + reply
                    : recentHistory + "\n\n用户: " + input + "\n助手: " + reply;

            String updatedSummary = historySummary;
            int newTurnIndex = turnIndex + 1;

            // 超过3轮时，把最早一轮压入摘要
            if (newTurnIndex > 3) {
                String[] rounds = updatedRecent.split("\n\n用户: ");
                if (rounds.length > 3) {
                    String oldestRound = "用户: " + rounds[0];
                    updatedSummary = historySummary.isBlank()
                            ? oldestRound
                            : historySummary + "\n" + oldestRound;
                    long summaryStart = System.nanoTime();
                    updatedSummary = historyManager.compressHistory(updatedSummary);
                    log.info("[ai_reply_timing] traceId={} stage=history_summary modelRole=SUMMARY elapsedMs={}",
                            traceId, elapsedMs(summaryStart));
                    // 保留最近3轮
                    updatedRecent = "用户: " + String.join("\n\n用户: ",
                            java.util.Arrays.copyOfRange(rounds, 1, rounds.length));
                    log.info("[ai_reply] history_compressed oldest_round_moved_to_summary");
                }
            }

            log.info("[ai_reply] reply={} turnIndex={}", reply, newTurnIndex);
            log.info("[ai_reply_timing] traceId={} stage=breakdown modelRole=ANSWER promptChars={} promptBuildMs={} modelCallMs={} answerGuardMs={} totalMs={}",
                    traceId, prompt.length(), promptBuildMs, modelCallMs, guardMs, elapsedMs(nodeStart));
            return Map.of("answer", reply,
                    "historySummary", updatedSummary,
                    "recentHistory", updatedRecent,
                    "turnIndex", newTurnIndex,
                    "promptBuildMs", promptBuildMs,
                    "modelCallMs", modelCallMs,
                    "answerGuardMs", guardMs,
                    "aiReplyTotalMs", elapsedMs(nodeStart),
                    "modelRole", ModelRole.ANSWER.name());
        });
    }

    private static long elapsedMs(long startNanos) {
        return Math.max(0L, (System.nanoTime() - startNanos) / 1_000_000L);
    }
}
