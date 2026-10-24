package com.teamblog.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    @Bean
    public AiSummaryClient aiSummaryClient(AiProperties properties) {
        return new AiSummaryClient(properties);
    }
}