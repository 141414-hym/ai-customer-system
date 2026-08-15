package com.aiagent.config;

import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * 知识库文档切分策略。
 *
 * 不同文档类型用不同切法，避免 TokenTextSplitter 把表格/FAQ 切碎：
 *
 * ┌──────────────────────┬──────────────────────────┐
 * │ 文档类型              │ 切分策略                  │
 * ├──────────────────────┼──────────────────────────┤
 * │ xp_pricing.md        │ 按 ## 标题切（每车型一块） │
 * │ xp_specs.md          │ 按 ## 标题切              │
 * │ xp_promotions.md     │ 按 ## 标题切              │
 * │ xp_aftersales.md     │ 按 ### 标题切             │
 * │ xp_faq.md            │ 按 Q&A 对切               │
 * │ xiaopeng_*.md        │ 按 ### 标题切（条款文档）  │
 * └──────────────────────┴──────────────────────────┘
 *
 * 使用方式：在 KnowledgeBaseConfig.loadAndSplitDocuments() 中替换 TokenTextSplitter：
 * <pre>
 *   Function<List<Document>, List<Document>> strategy = KnowledgeSplitter.strategyFor(source);
 *   List<Document> chunks = strategy.apply(docs);
 * </pre>
 */
public class KnowledgeSplitter {

    /** 按 Markdown 二级标题切分 */
    private static final Pattern H2_PATTERN = Pattern.compile("(?=^## )", Pattern.MULTILINE);

    /** 按 Markdown 三级标题切分 */
    private static final Pattern H3_PATTERN = Pattern.compile("(?=^### )", Pattern.MULTILINE);

    /** 按 FAQ 问答对切分 */
    private static final Pattern FAQ_PATTERN = Pattern.compile("(?=^\\*\\*Q：)", Pattern.MULTILINE);

    /**
     * 根据文件名选择切分策略。
     * 未匹配的文档使用默认策略（按 ## 标题切）。
     */
    public static Function<List<Document>, List<Document>> strategyFor(String source) {
        if (source == null) {
            return KnowledgeSplitter::splitByH2;
        }

        if (source.startsWith("xp_faq")) {
            return KnowledgeSplitter::splitByFAQ;
        }
        if (source.startsWith("xp_pricing") || source.startsWith("xp_specs")
                || source.startsWith("xp_promotions")) {
            return KnowledgeSplitter::splitByH2;
        }
        if (source.startsWith("xp_aftersales") || source.startsWith("xiaopeng_")) {
            return KnowledgeSplitter::splitByH3;
        }

        // 默认按 ## 标题切
        return KnowledgeSplitter::splitByH2;
    }

    /** 按二级标题切，每个 ## 段为一个 chunk */
    public static List<Document> splitByH2(List<Document> docs) {
        return splitByPattern(docs, H2_PATTERN);
    }

    /** 按三级标题切，每个 ### 段为一个 chunk */
    public static List<Document> splitByH3(List<Document> docs) {
        return splitByPattern(docs, H3_PATTERN);
    }

    /** 按 FAQ 问答对切 */
    public static List<Document> splitByFAQ(List<Document> docs) {
        return splitByPattern(docs, FAQ_PATTERN);
    }

    private static List<Document> splitByPattern(List<Document> docs, Pattern pattern) {
        List<Document> result = new ArrayList<>();
        for (Document doc : docs) {
            String text = doc.getText();
            String[] parts = pattern.split(text);
            for (String part : parts) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    Document chunk = new Document(trimmed, new java.util.HashMap<>(doc.getMetadata()));
                    result.add(chunk);
                }
            }
        }
        return result.isEmpty() ? docs : result; // 切不出来就保留原文档
    }
}
