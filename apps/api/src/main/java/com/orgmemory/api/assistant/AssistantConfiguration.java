package com.orgmemory.api.assistant;

import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.knowledge.SecureKnowledgeRetrievalService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
class AssistantConfiguration {

    @Bean
    AssistantService assistantService(
            SecureKnowledgeRetrievalService retrieval,
            ChatModelPort chat) {
        return new AssistantService(retrieval, chat);
    }
}
