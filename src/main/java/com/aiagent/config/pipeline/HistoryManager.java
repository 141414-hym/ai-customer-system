package com.aiagent.config.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

/**
 * 对话历史管理：压缩超过3轮的早期历史为摘要。
 */
@Component
public class HistoryManager {

    private static final Logger log = LoggerFactory.getLogger(HistoryManager.class);

    public String compressHistory(ChatModel chatModel, String historySummary) {
        log.info("[compress_history] compressing {} chars of history", historySummary.length());
        ChatClient client = ChatClient.builder(chatModel).build();
        String prompt = """
                请将以下对话历史压缩为一段简洁摘要，保留关键信息（用户问题、处理结果、未解决问题等），去掉冗余细节。

                对话历史：
                %s

                只输出摘要文本，不要加前缀说明。
                """.formatted(historySummary);
        String compressed = client.prompt().user(prompt).call().content();
        String result = compressed == null ? historySummary : compressed;
        log.info("[compress_history] result={}", result);
        return result;
    }
}
