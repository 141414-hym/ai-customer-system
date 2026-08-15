package com.aiagent.config;

import org.springframework.ai.document.Document;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 知识库文档切分 + 导出工具。
 *
 * 按文档类型用不同切分策略，验证效果后导出为 JSONL（每行一个 chunk）。
 *
 * 用法：直接跑 main()，输出到 knowledge_base/chunks/
 */
public class KnowledgeSplitter {

    private static final String KB_IN = "knowledge_base";
    private static final String KB_OUT = "knowledge_base/chunks";

    /** 按 Markdown 二级标题切分 (##) */
    private static final Pattern H2 = Pattern.compile("(?=^## )", Pattern.MULTILINE);

    /** 按 Markdown 三级标题切分 (###) */
    private static final Pattern H3 = Pattern.compile("(?=^### )", Pattern.MULTILINE);

    /** 按 FAQ 问答对切分 (**Q：) */
    private static final Pattern FAQ = Pattern.compile("(?=^\\*\\*Q：)", Pattern.MULTILINE);

    /** 按数字条款标题切分（缩进+数字. 开头） — 官网爬取文档专用 */
    private static final Pattern NUM_CLAUSE = Pattern.compile("(?=^\\s{2,}\\d+\\. )", Pattern.MULTILINE);

    /** 文件名 → 切分策略 */
    private static final Map<String, Pattern> STRATEGY = new LinkedHashMap<>();
    static {
        // FAQ 按问答对切
        STRATEGY.put("xp_faq", FAQ);
        // 结构化表格文档按 ## 切（每个车型/板块一个 chunk）
        STRATEGY.put("xp_pricing", H2);
        STRATEGY.put("xp_specs", H2);
        STRATEGY.put("xp_promotions", H2);
        // 自编售后文档按 ### 切（有明确的 Markdown 标题）
        STRATEGY.put("xp_aftersales", H3);
        // 官网爬取文档：整篇不拆 —— 篇幅短，拆了反而丢上下文
        // xiaopeng_roadassistance / xiaopeng_sales / xiaopeng_dianchi → null → 不切
    }

    /**
     * 切分并导出所有知识库文档。
     */
    public static void main(String[] args) throws IOException {
        Path inDir = Paths.get(KB_IN);
        Path outDir = Paths.get(KB_OUT);
        Files.createDirectories(outDir);

        int totalChunks = 0;
        int totalFiles = 0;

        try (Stream<Path> files = Files.list(inDir)) {
            List<Path> mdFiles = files
                    .filter(p -> p.toString().endsWith(".md"))
                    .sorted()
                    .toList();

            for (Path file : mdFiles) {
                String name = file.getFileName().toString();
                String base = name.replace(".md", "");
                Pattern pattern = pickPattern(name);

                String raw = Files.readString(file, StandardCharsets.UTF_8);
                List<String> chunks = split(raw, pattern);

                // 导出为 JSONL
                Path outFile = outDir.resolve(base + ".jsonl");
                try (BufferedWriter w = Files.newBufferedWriter(outFile, StandardCharsets.UTF_8)) {
                    for (int i = 0; i < chunks.size(); i++) {
                        Map<String, Object> record = new LinkedHashMap<>();
                        record.put("id", base + "_" + i);
                        record.put("source", name);
                        record.put("chunk_index", i);
                        record.put("content", chunks.get(i));
                        w.write(toJsonLine(record));
                        w.newLine();
                    }
                }

                System.out.printf("  %-35s  策略=%-6s  标题=%2d  切出=%2d chunk%n",
                        name, patternName(pattern), countHeadings(raw, pattern), chunks.size());
                totalChunks += chunks.size();
                totalFiles++;
            }
        }

        System.out.printf("%n总计: %d 文件 → %d chunks → %s%n", totalFiles, totalChunks, outDir.toAbsolutePath());
    }

    /** 选择切分策略，返回 null 表示整篇不拆 */
    static Pattern pickPattern(String fileName) {
        for (Map.Entry<String, Pattern> e : STRATEGY.entrySet()) {
            if (fileName.startsWith(e.getKey())) {
                return e.getValue();
            }
        }
        return null; // 不匹配的文档：整篇保留，不切
    }

    /** 按正则切分，过滤空白段。pattern 为 null 时整篇不拆 */
    static List<String> split(String text, Pattern pattern) {
        if (pattern == null) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? List.of() : List.of(trimmed);
        }
        List<String> result = new ArrayList<>();
        String[] parts = pattern.split(text);
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    /** 数一下切分前的标题数量，作为参考基准 */
    private static int countHeadings(String text, Pattern pattern) {
        if (pattern == null) return 0;
        if (pattern == FAQ) return (int) Pattern.compile("\\*\\*Q：").matcher(text).results().count();
        return (int) pattern.matcher(text).results().count();
    }

    private static String patternName(Pattern p) {
        if (p == H2) return "##标题";
        if (p == H3) return "###标题";
        if (p == FAQ) return "FAQ对";
        if (p == null) return "整篇";
        return "默认";
    }

    /** 简单手写 JSON，不引入 Jackson */
    static String toJsonLine(Map<String, Object> record) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : record.entrySet()) {
            if (!first) sb.append(", ");
            sb.append("\"").append(escape(e.getKey())).append("\": ");
            Object v = e.getValue();
            if (v instanceof String) {
                sb.append("\"").append(escape((String) v)).append("\"");
            } else {
                sb.append(v);
            }
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
