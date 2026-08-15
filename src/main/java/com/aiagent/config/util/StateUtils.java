package com.aiagent.config.util;

import com.aiagent.dto.EntryContext;
import com.alibaba.cloud.ai.graph.OverAllState;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 状态读取与通用工具方法，全部 static。
 */
public final class StateUtils {

    public static final java.util.List<String> ROUTES = java.util.List.of(
            "faq", "knowledge_base", "business_tool", "external_search", "human_handoff", "clarify"
    );

    private StateUtils() {}

    // ========== 状态读取 ==========

    public static String readValue(OverAllState state, String key) {
        try {
            Object value = state.value(key).orElse(null);
            return value == null ? "" : String.valueOf(value);
        } catch (Exception ex) {
            return "";
        }
    }

    public static boolean readBoolean(OverAllState state, String key) {
        return Boolean.parseBoolean(readValue(state, key));
    }

    public static int readInt(OverAllState state, String key, int fallback) {
        try {
            String value = readValue(state, key);
            return value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    // ========== 上下文构建 ==========

    public static EntryContext buildEntryContext(OverAllState state) {
        String input = readValue(state, "input");
        String sessionId = readValue(state, "sessionId");
        String tenantId = readValue(state, "tenantId");
        String productCode = readValue(state, "productCode");
        String locale = fallback(readValue(state, "locale"), "zh-CN");
        String historySummary = readValue(state, "historySummary");
        String recentHistory = readValue(state, "recentHistory");
        String lastRoute = readValue(state, "lastRoute");
        boolean lastNeedClarify = readBoolean(state, "lastNeedClarify");
        String pendingQuestion = readValue(state, "pendingQuestion");
        int turnIndex = readInt(state, "turnIndex", 1);
        return new EntryContext(sessionId, tenantId, productCode, locale, input, historySummary, recentHistory,
                lastRoute, lastNeedClarify, pendingQuestion, turnIndex);
    }

    // ========== 通用工具 ==========

    public static String normalize(String input) {
        return input == null ? "" : input.trim().replaceAll("\\s+", " ");
    }

    public static Map<String, Double> baseScores() {
        Map<String, Double> scores = new HashMap<>();
        for (String route : ROUTES) {
            scores.put(route, 0d);
        }
        return scores;
    }

    public static String maxKey(Map<String, Double> scores) {
        String best = ROUTES.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Map.Entry<String, Double> entry : scores.entrySet()) {
            if (entry.getValue() > bestScore) {
                bestScore = entry.getValue();
                best = entry.getKey();
            }
        }
        return best;
    }

    public static double normalizeScore(double rawScore) {
        return Math.max(0d, Math.min(1d, rawScore / 100d));
    }

    public static boolean containsAny(String input, String... values) {
        for (String value : values) {
            if (input.contains(value)) {
                return true;
            }
        }
        return false;
    }

    // ========== JSON 解析 ==========

    public static String extractJsonValue(String content, String key) {
        Pattern pattern = Pattern.compile(
                "\\\"" + Pattern.quote(key) + "\\\"\\s*:\\s*(\\\"([^\\\"]*)\\\"|true|false|[-0-9.]+)",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String raw = matcher.group(1);
            if (raw.startsWith("\"") && raw.endsWith("\"")) {
                return raw.substring(1, raw.length() - 1);
            }
            return raw;
        }
        return null;
    }

    public static String parseRoute(String text, String fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        String route = text.trim().toLowerCase();
        return switch (route) {
            case "faq", "knowledge_base", "business_tool", "external_search", "human_handoff", "clarify" -> route;
            default -> fallback;
        };
    }

    public static double safeDouble(String text, double fallback) {
        if (text == null || text.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return fallback;
        }
    }

    public static String valueOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
