package com.aiagent.config.pipeline;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import com.aiagent.config.model.ModelRegistry;
import com.aiagent.config.model.ModelRole;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * LLM 改写层：结合历史上下文补全省略指代、纠正错别字/拼音、拆解多意图长句。
 * 返回子问题列表。单意图时列表只有一个元素。
 */
@Component
public class QueryRewriter {

    private static final Logger log = LoggerFactory.getLogger(QueryRewriter.class);

    private final ModelRegistry modelRegistry;

    public QueryRewriter(ModelRegistry modelRegistry) { this.modelRegistry = modelRegistry; }

    public List<String> rewriteQuery(String cleanedInput,
                                     String historySummary, String recentHistory) {
        if (cleanedInput == null || cleanedInput.isBlank()) {
            return List.of();
        }

        ChatClient client = modelRegistry.client(ModelRole.REWRITE);
        StringBuilder historyContext = new StringBuilder();
        if (!historySummary.isBlank()) {
            historyContext.append("更早对话摘要：").append(historySummary).append("\n");
        }
        if (!recentHistory.isBlank()) {
            historyContext.append("最近对话：\n").append(recentHistory).append("\n");
        }

        String prompt = """
                # 角色
                你是查询优化助手。根据对话历史和用户当前输入，生成用于知识库检索的查询。

                # 任务
                1. 如果用户输入省略了上下文（如指代词"它""那个""高配""对比"），请根据历史对话补全为完整问题
                2. 如果用户输入包含拼音（如"duoshao qian"），请根据上下文还原为中文
                3. 如果用户输入有明显同音错别字（如"多少浅""优慧"），请修正为正确表达
                4. 去除输入中无意义的冗余词和重复
                5. 如果用户输入包含多个不同意图（如同时问价格、配置、优惠），请拆解为多个独立子问题

                # 限制
                - 如果用户输入是简单的寒暄/闲聊（如"你好""在吗""谢谢""ok""嗯"），输出空数组：[]
                - 只输出 JSON 字符串数组，不要其他任何文字
                - 每个元素是一个独立完整的检索查询语句
                - 不要改变用户原始意图，不要添加用户没问的问题

                # 输出格式示例
                单问题：["G6高配版多少钱"]
                多问题：["新款和老款区别","新款质量怎么样","新款有什么优惠","老客户增购福利"]
                闲聊：[]

                # 对话历史
                %s

                # 用户当前输入
                %s
                """.formatted(historyContext.toString(), cleanedInput);

        String raw = client.prompt().user(prompt).call().content();
        if (raw == null || raw.isBlank()) {
            return List.of(cleanedInput);
        }
        raw = raw.trim();

        List<String> subQueries = parseStringArray(raw);
        if (subQueries.isEmpty()) {
            subQueries = List.of(cleanedInput);
        }
        subQueries = subQueries.stream()
                .filter(s -> !s.isBlank())
                .collect(Collectors.toList());
        if (subQueries.isEmpty()) {
            return List.of(cleanedInput);
        }
        log.info("[rewrite_query] input=\"{}\" sub_queries={}", cleanedInput, subQueries);
        return subQueries;
    }

    /** 从 LLM 返回的内容中解析 JSON 字符串数组 */
    private List<String> parseStringArray(String raw) {
        Pattern arrayPattern = Pattern.compile("\\[.*?\\]", Pattern.DOTALL);
        Matcher m = arrayPattern.matcher(raw);
        if (!m.find()) {
            return List.of();
        }
        String arrayJson = m.group(0);
        List<String> result = new ArrayList<>();
        Pattern strPattern = Pattern.compile("\"([^\"]*)\"");
        Matcher strMatcher = strPattern.matcher(arrayJson);
        while (strMatcher.find()) {
            result.add(strMatcher.group(1));
        }
        return result;
    }
}
