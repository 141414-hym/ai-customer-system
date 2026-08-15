package com.aiagent.config.pipeline;

import com.aiagent.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * RAG 检索层：向量检索 + BM25 关键词检索双路 + RRF 融合。
 */
@Component
public class Retriever {

    private static final Logger log = LoggerFactory.getLogger(Retriever.class);

    private static final double RRF_K = 60.0;

    private final VectorStore vectorStore;
    private final Bm25Retriever bm25Retriever;
    private final AppProperties appProperties;

    public Retriever(VectorStore vectorStore, Bm25Retriever bm25Retriever, AppProperties appProperties) {
        this.vectorStore = vectorStore;
        this.bm25Retriever = bm25Retriever;
        this.appProperties = appProperties;
    }

    public String retrieveMultiQuery(List<String> queries) {
        if (queries == null || queries.isEmpty()) {
            return "【检索结果为空】无有效查询。";
        }
        if (queries.size() == 1) {
            return retrieveSingle(queries.get(0));
        }

        int topK = appProperties.getRag().getTopK();
        double threshold = appProperties.getRag().getSimilarityThreshold();
        log.info("[rag] multi_query: {} sub-queries, topK={}, threshold={}", queries.size(), topK, threshold);

        Map<String, RrfDoc> rrfMap = new LinkedHashMap<>();

        for (String query : queries) {
            if (query.isBlank()) continue;

            List<Document> vectorResults = searchVector(query, topK, threshold);
            List<Bm25Retriever.ScoredDoc> bm25Results = bm25Retriever.search(query, topK);

            int rank = 1;
            for (Document doc : vectorResults) {
                String docId = doc.getId();
                double vectorScore = extractVectorScore(doc);
                rrfMap.merge(docId, new RrfDoc(doc, 1.0 / (RRF_K + rank), vectorScore, null), RrfDoc::merge);
                rank++;
            }
            rank = 1;
            for (Bm25Retriever.ScoredDoc sd : bm25Results) {
                String docId = sd.getDocument().getId();
                double bm25Score = sd.getScore();
                rrfMap.merge(docId, new RrfDoc(sd.getDocument(), 1.0 / (RRF_K + rank), null, bm25Score), RrfDoc::merge);
                rank++;
            }

            log.info("[rag] sub_query=\"{}\" vector={} bm25={}", query, vectorResults.size(), bm25Results.size());
        }

        List<RrfDoc> merged = rrfMap.values().stream()
                .sorted((a, b) -> Double.compare(b.rrfScore, a.rrfScore))
                .limit(topK * 2L)
                .collect(Collectors.toList());

        log.info("[rag] multi_query: {} sub-queries → {} unique merged (rrf)", queries.size(), merged.size());
        return formatRrfResults(merged);
    }

    public String retrieveSingle(String query) {
        if (query == null || query.isBlank()) {
            return "【检索结果为空】用户输入为空，无法检索。";
        }

        int topK = appProperties.getRag().getTopK();
        double threshold = appProperties.getRag().getSimilarityThreshold();
        log.info("[rag] query={} topK={} threshold={}", query, topK, threshold);

        List<Document> vectorResults = searchVector(query, topK, threshold);
        List<Bm25Retriever.ScoredDoc> bm25Results = bm25Retriever.search(query, topK);

        Map<String, RrfDoc> rrfMap = new LinkedHashMap<>();
        int rank = 1;
        for (Document doc : vectorResults) {
            rrfMap.merge(doc.getId(), new RrfDoc(doc, 1.0 / (RRF_K + rank), extractVectorScore(doc), null), RrfDoc::merge);
            rank++;
        }
        rank = 1;
        for (Bm25Retriever.ScoredDoc sd : bm25Results) {
            rrfMap.merge(sd.getDocument().getId(), new RrfDoc(sd.getDocument(), 1.0 / (RRF_K + rank), null, sd.getScore()), RrfDoc::merge);
            rank++;
        }

        List<RrfDoc> merged = rrfMap.values().stream()
                .sorted((a, b) -> Double.compare(b.rrfScore, a.rrfScore))
                .limit(topK * 2L)
                .collect(Collectors.toList());

        log.info("[rag] single_query: vector={} bm25={} rrf_merged={}",
                vectorResults.size(), bm25Results.size(), merged.size());
        return formatRrfResults(merged);
    }

    private List<Document> searchVector(String query, int topK, double threshold) {
        return vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build());
    }

    /**
     * 格式化 RRF 融合结果，输出三个值：
     * - 向量得分（vector）：命中则显示，未命中显示 "-"
     * - BM25 得分（bm25）：命中则显示，未命中显示 "-"
     * - RRF 排名：融合后的排序位置
     */
    private String formatRrfResults(List<RrfDoc> docs) {
        StringBuilder sb = new StringBuilder();
        sb.append("【知识库检索结果】\n");
        for (int i = 0; i < docs.size(); i++) {
            RrfDoc r = docs.get(i);
            Document doc = r.doc;
            String source = (String) doc.getMetadata().getOrDefault("source", "未知来源");

            String vectorStr = r.vectorScore != null
                    ? String.format("%.2f", r.vectorScore)
                    : "-";
            String bm25Str = r.bm25Score != null
                    ? String.format("%.3f", r.bm25Score)
                    : "-";

            sb.append("---\n");
            sb.append("来源: ").append(source)
                    .append(" | 向量得分: ").append(vectorStr)
                    .append(" | BM25得分: ").append(bm25Str)
                    .append(" | RRF排名: ").append(i + 1)
                    .append(" | RRF总分: ").append(String.format("%.4f", r.rrfScore))
                    .append("\n");
            sb.append(doc.getText()).append("\n");
        }
        sb.append("---\n以上共 ").append(docs.size()).append(" 条检索结果。");
        return sb.toString();
    }

    private Double extractVectorScore(Document doc) {
        if (doc.getMetadata().containsKey("distance")) {
            return (double) doc.getMetadata().get("distance");
        }
        return null;
    }

    /** RRF 融合辅助类：记录向量得分、BM25 得分、RRF 总分 */
    private static class RrfDoc {
        final Document doc;
        double rrfScore;
        Double vectorScore;
        Double bm25Score;

        RrfDoc(Document doc, double rrfScore, Double vectorScore, Double bm25Score) {
            this.doc = doc;
            this.rrfScore = rrfScore;
            this.vectorScore = vectorScore;
            this.bm25Score = bm25Score;
        }

        RrfDoc merge(RrfDoc other) {
            this.rrfScore += other.rrfScore;
            if (other.vectorScore != null) {
                this.vectorScore = other.vectorScore;
            }
            if (other.bm25Score != null) {
                this.bm25Score = other.bm25Score;
            }
            return this;
        }
    }
}