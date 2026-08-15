package com.aiagent.config.pipeline;

import org.apache.lucene.analysis.cjk.CJKAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 内存 BM25 关键词检索器。
 * 使用 Lucene CJKAnalyzer（Bigram 二元切分）做中文分词，适合中文场景。
 * 启动时加载所有 chunk 建立倒排索引，查询时计算 BM25 分数。
 */
@Component
public class Bm25Retriever {

    private static final Logger log = LoggerFactory.getLogger(Bm25Retriever.class);

    // BM25 参数
    private static final double K1 = 1.2d;
    private static final double B = 0.75d;

    /** 倒排索引：词 → (文档ID → 词频) */
    private final Map<String, Map<String, Integer>> invertedIndex = new HashMap<>();

    /** 文档ID → 文档 */
    private final Map<String, Document> documents = new LinkedHashMap<>();

    /** 文档ID → 文档长度（词数） */
    private final Map<String, Integer> docLengths = new HashMap<>();

    /** 平均文档长度 */
    private double avgDocLength = 0;

    /** 文档总数 */
    private int totalDocs = 0;

    /** CJKAnalyzer 实例（线程安全） */
    private final CJKAnalyzer analyzer = new CJKAnalyzer();

    /** 是否已初始化 */
    private volatile boolean initialized = false;

    /**
     * 添加文档到 BM25 索引。
     * 由 KnowledgeBaseConfig 在加载完 jsonl 后调用。
     */
    public synchronized void addDocuments(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return;
        }
        for (Document doc : docs) {
            String docId = doc.getId();
            // 如果 doc.getId() 是自动生成的 UUID，改用 metadata 中的 chunk_id
            if (docId == null || docId.length() > 20) {
                Object chunkId = doc.getMetadata().get("chunk_id");
                if (chunkId != null) {
                    docId = String.valueOf(chunkId);
                } else {
                    docId = "doc_" + documents.size();
                }
            }
            documents.put(docId, doc);

            List<String> tokens = tokenize(doc.getText());
            docLengths.put(docId, tokens.size());

            Map<String, Integer> termFreq = new HashMap<>();
            for (String token : tokens) {
                termFreq.merge(token, 1, Integer::sum);
            }
            for (Map.Entry<String, Integer> entry : termFreq.entrySet()) {
                invertedIndex.computeIfAbsent(entry.getKey(), k -> new HashMap<>())
                        .put(docId, entry.getValue());
            }
        }
        totalDocs = documents.size();
        avgDocLength = docLengths.values().stream().mapToInt(Integer::intValue).average().orElse(1.0);
        initialized = true;
        log.info("[bm25] index built: {} docs, {} unique terms, avgDocLen={}",
                totalDocs, invertedIndex.size(), String.format("%.1f", avgDocLength));
    }

    /**
     * 建立索引。
     * 在启动时由 Retriever 调用，传入所有 chunk。
     */
    /**
     * 对 query 做 BM25 检索，返回按分数降序的文档列表。
     */
    public List<ScoredDoc> search(String query, int topK) {
        if (!initialized || totalDocs == 0) {
            return List.of();
        }

        List<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return List.of();
        }

        // 对每个文档计算 BM25 分数
        Map<String, Double> scores = new HashMap<>();
        for (String docId : documents.keySet()) {
            double score = 0;
            int docLen = docLengths.get(docId);

            for (String term : queryTokens) {
                Map<String, Integer> posting = invertedIndex.get(term);
                if (posting == null) continue;

                int tf = posting.getOrDefault(docId, 0);
                if (tf == 0) continue;

                int df = posting.size(); // 包含该词的文章数
                int n = totalDocs;

                // BM25公式
                double idf = Math.log((n - df + 0.5) / (df + 0.5) + 1.0);
                double tfNorm = tf * (K1 + 1) / (tf + K1 * (1 - B + B * docLen / avgDocLength));
                score += idf * tfNorm;
            }

            if (score > 0) {
                scores.put(docId, score);
            }
        }

        // 按分数降序
        return scores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> new ScoredDoc(documents.get(e.getKey()), e.getValue()))
                .collect(Collectors.toList());
    }

    /**
     * 用 CJKAnalyzer 做中文分词。
     * CJKAnalyzer 对中日韩文字做 Bigram 二元切分，字母数字保留原词。
     */
    private List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        List<String> tokens = new ArrayList<>();
        try {
            var stream = analyzer.tokenStream("", new StringReader(text));
            stream.reset();
            while (stream.incrementToken()) {
                String term = stream.getAttribute(CharTermAttribute.class).toString();
                if (term.length() > 0) {
                    tokens.add(term);
                }
            }
            stream.end();
            stream.close();
        } catch (Exception e) {
            log.warn("[bm25] tokenize error: {}", e.getMessage());
        }
        return tokens;
    }

    /**
     * BM25 检索结果，包含文档和分数。
     */
    public static class ScoredDoc {
        private final Document document;
        private final double score;

        public ScoredDoc(Document document, double score) {
            this.document = document;
            this.score = score;
        }

        public Document getDocument() { return document; }
        public double getScore() { return score; }
    }
}