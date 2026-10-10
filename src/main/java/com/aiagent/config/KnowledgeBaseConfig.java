package com.aiagent.config;

import com.aiagent.config.pipeline.Bm25Retriever;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * RAG 知识库配置：向量化模型 + 向量存储。
 *
 * 数据流：
 *   chunks/*.jsonl（预切分） → Document → EmbeddingModel → SimpleVectorStore
 *                                                                    ↓
 *                                              vector_store.json（持久化缓存）
 *
 * 首次启动：读 JSONL → 调 API 向量化 → save() 落盘
 * 后续启动：load() 直接恢复，零 API 调用
 *
 * 知识库更新时：删除 vector_store.json，重启自动重建
 */
@Configuration
public class KnowledgeBaseConfig {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseConfig.class);

    private static final String CHUNKS_DIR = "knowledge_base/chunks";
    private static final String CACHE_FILE = "knowledge_base/chunks/vector_store.json";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final Bm25Retriever bm25Retriever;

    public KnowledgeBaseConfig(Bm25Retriever bm25Retriever) {
        this.bm25Retriever = bm25Retriever;
    }

    /** DashScope API实例（配置值来自AI_DASHSCOPE_API_KEY环境变量）。 */
    @Bean
    public DashScopeApi dashScopeApi(
            @org.springframework.beans.factory.annotation.Value("${spring.ai.dashscope.api-key}") String apiKey) {
        log.info("初始化 DashScopeApi");
        return DashScopeApi.builder().apiKey(apiKey).build();
    }

    /** 向量化模型，百炼 text-embedding-v3, 512 维 */
    @Bean
    public EmbeddingModel embeddingModel(DashScopeApi dashScopeApi) {
        log.info("初始化 EmbeddingModel: text-embedding-v3 (dimension=512)");
        return new DashScopeEmbeddingModel(
                dashScopeApi,
                MetadataMode.EMBED,
                DashScopeEmbeddingOptions.builder()
                        .withModel("text-embedding-v3")
                        .withDimensions(512)
                        .build()
        );
    }

    /** 向量存储：优先从缓存恢复，没有则从 JSONL 重建 */
    @Bean
    public VectorStore vectorStore(EmbeddingModel embeddingModel) {
        SimpleVectorStore store = SimpleVectorStore.builder(embeddingModel).build();
        File cache = new File(CACHE_FILE);

        List<Document> chunks;

        if (cache.exists()) {
            // === 缓存命中：直接恢复 ===
            log.info("从缓存恢复向量库: {}", cache.getAbsolutePath());
            store.load(cache);
            log.info("向量库恢复完成");
            // 从缓存恢复后也需要加载 chunks 给 BM25（不调向量化API，只读jsonl）
            chunks = loadJsonlChunks();
        } else {
            // === 首次启动：加载 JSONL → 向量化 → 持久化 ===
            log.info("缓存不存在，从 JSONL 重建向量库...");
            chunks = loadJsonlChunks();
            if (chunks.isEmpty()) {
                log.warn("未找到任何 chunk 文件: {}", Paths.get(CHUNKS_DIR).toAbsolutePath());
            } else {
                log.info("开始向量化 {} 个 chunk（首次启动，将调用百炼 API）...", chunks.size());
                store.add(chunks);
                store.save(cache);
                log.info("向量库已保存到缓存: {} 个 chunk → {}", chunks.size(), cache.getAbsolutePath());
            }
        }

        // 建立 BM25 索引
        if (!chunks.isEmpty()) {
            bm25Retriever.addDocuments(chunks);
        }

        return store;
    }

    /** 从 knowledge_base/chunks/*.jsonl 加载预切分好的文档块 */
    private List<Document> loadJsonlChunks() {
        List<Document> docs = new ArrayList<>();
        Path dir = Paths.get(CHUNKS_DIR);

        if (!Files.isDirectory(dir)) {
            log.warn("chunks 目录不存在: {}", dir.toAbsolutePath());
            return docs;
        }

        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> p.toString().endsWith(".jsonl"))
                    .sorted()
                    .forEach(filePath -> {
                        try (BufferedReader r = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)) {
                            String line;
                            while ((line = r.readLine()) != null) {
                                Map<String, Object> record = objectMapper.readValue(line,
                                        new TypeReference<Map<String, Object>>() {});
                                if (record == null) continue;

                                String content = (String) record.getOrDefault("content", "");
                                String source = (String) record.getOrDefault("source", "unknown");
                                String id = (String) record.getOrDefault("id", source);

                                Map<String, Object> metadata = new java.util.HashMap<>();
                                metadata.put("source", source);
                                metadata.put("chunk_id", id);
                                copyMetadata(record, metadata, "tenantId");
                                copyMetadata(record, metadata, "productCode");
                                copyMetadata(record, metadata, "locale");
                                Document doc = new Document(content, metadata);
                                docs.add(doc);
                            }
                            log.debug("  加载: {} → {} chunks", filePath.getFileName(), docs.size());
                        } catch (IOException e) {
                            log.error("读取 chunk 文件失败: {}", filePath, e);
                        }
                    });
        } catch (IOException e) {
            log.error("遍历 chunks 目录失败", e);
        }

        return docs;
    }

    private static void copyMetadata(Map<String, Object> source, Map<String, Object> target, String key) {
        Object value = source.get(key);
        if (value != null && !String.valueOf(value).isBlank()) target.put(key, String.valueOf(value));
    }
}
