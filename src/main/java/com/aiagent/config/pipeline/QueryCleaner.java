package com.aiagent.config.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 规则层清洗：去重复标点/字符、归一化空白、全角转半角。
 * 不做纠错和拼音转换，这些留给 LLM 改写层。
 */
@Component
public class QueryCleaner {

    private static final Logger log = LoggerFactory.getLogger(QueryCleaner.class);

    public String cleanQuery(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) {
            return "";
        }
        String cleaned = rawInput.trim().replaceAll("\\s+", " ");
        // 全角符号转半角
        cleaned = cleaned.replace('！', '!')
                .replace('？', '?')
                .replace('，', ',')
                .replace('。', '.')
                .replace('；', ';')
                .replace('：', ':')
                .replace('‘', '\'')
                .replace('’', '\'')
                .replace('"', '"')
                .replace('"', '"');
        // 去连续重复标点（3个以上→1个）
        cleaned = cleaned.replaceAll("([!?,.;:。，；：])\\1{2,}", "$1");
        // 去连续重复中文字符（3个以上→1个）
        cleaned = cleaned.replaceAll("([\\u4e00-\\u9fff])\\1{2,}", "$1");
        // 去连续重复字母（4个以上→1个）
        cleaned = cleaned.replaceAll("([a-zA-Z])\\1{3,}", "$1");

        String result = cleaned.trim();
        if (!result.equals(rawInput.trim())) {
            log.info("[clean_query] raw=\"{}\" cleaned=\"{}\"", rawInput, result);
        }
        return result;
    }
}
