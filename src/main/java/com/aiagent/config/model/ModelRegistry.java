package com.aiagent.config.model;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class ModelRegistry {
    private final ChatModel chatModel;
    private final Map<ModelRole, String> models = new EnumMap<>(ModelRole.class);

    public ModelRegistry(ChatModel chatModel,
                         @Value("${assistant.models.route:qwen-turbo}") String route,
                         @Value("${assistant.models.rewrite:qwen-turbo}") String rewrite,
                         @Value("${assistant.models.answer:qwen-plus}") String answer,
                         @Value("${assistant.models.summary:qwen-turbo}") String summary) {
        this.chatModel = chatModel;
        models.put(ModelRole.ROUTE, route);
        models.put(ModelRole.REWRITE, rewrite);
        models.put(ModelRole.ANSWER, answer);
        models.put(ModelRole.SUMMARY, summary);
    }

    public ChatClient client(ModelRole role) {
        DashScopeChatOptions options = new DashScopeChatOptions();
        options.setModel(models.get(role));
        options.setTemperature(role == ModelRole.ANSWER ? 0.1 : 0.0);
        return ChatClient.builder(chatModel).defaultOptions(options).build();
    }

    public String model(ModelRole role) { return models.get(role); }
    public ChatModel baseModel() { return chatModel; }
}
