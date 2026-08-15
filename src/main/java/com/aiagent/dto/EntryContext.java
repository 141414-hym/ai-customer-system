package com.aiagent.dto;

import java.util.Map;

public record EntryContext(String sessionId,
                           String tenantId,
                           String productCode,
                           String locale,
                           String inputText,
                           String historySummary,
                           String recentHistory,
                           String lastRoute,
                           boolean lastNeedClarify,
                           String pendingQuestion,
                           int turnIndex) {
    public String contextText() {
        return String.join("\n",
                "历史摘要: " + safe(historySummary),
                "最近对话: " + safe(recentHistory),
                "上一轮路由: " + safe(lastRoute),
                "上一轮是否补问: " + lastNeedClarify,
                "待补问题: " + safe(pendingQuestion),
                "本轮输入: " + safe(inputText));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
