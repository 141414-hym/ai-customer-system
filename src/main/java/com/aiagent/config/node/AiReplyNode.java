package com.aiagent.config.node;

import com.aiagent.config.pipeline.HistoryManager;
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

    private final ChatModel chatModel;
    private final HistoryManager historyManager;

    public AiReplyNode(ChatModel chatModel, HistoryManager historyManager) {
        this.chatModel = chatModel;
        this.historyManager = historyManager;
    }

    public AsyncNodeAction getAction() {
        return node_async(state -> {
            String input = readValue((OverAllState) state, "input");
            String answer = readValue((OverAllState) state, "answer");
            String subQueriesRaw = readValue((OverAllState) state, "subQueries");
            String historySummary = readValue((OverAllState) state, "historySummary");
            String recentHistory = readValue((OverAllState) state, "recentHistory");
            int turnIndex = readInt((OverAllState) state, "turnIndex", 0);
            log.info("[ai_reply] input={} answer={} turnIndex={}", input, answer, turnIndex);

            // 构建历史上下文
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

            String prompt = promptBuilder.toString();
            ChatClient client = ChatClient.builder(chatModel).build();
            String reply = client.prompt().user(prompt).call().content();

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
                    updatedSummary = historyManager.compressHistory(chatModel, updatedSummary);
                    // 保留最近3轮
                    updatedRecent = "用户: " + String.join("\n\n用户: ",
                            java.util.Arrays.copyOfRange(rounds, 1, rounds.length));
                    log.info("[ai_reply] history_compressed oldest_round_moved_to_summary");
                }
            }

            log.info("[ai_reply] reply={} turnIndex={}", reply, newTurnIndex);
            return Map.of("answer", reply,
                    "historySummary", updatedSummary,
                    "recentHistory", updatedRecent,
                    "turnIndex", newTurnIndex);
        });
    }
}
