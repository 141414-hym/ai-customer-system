package com.aiagent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AppProperties.class)
public class AgentConfiguration {

    @Bean
    public AppProperties appProperties() {
        return new AppProperties();
    }
}
