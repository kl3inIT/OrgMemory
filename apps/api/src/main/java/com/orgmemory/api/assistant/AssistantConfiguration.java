package com.orgmemory.api.assistant;

import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.assistant.AssistantAssetToolService;
import com.orgmemory.core.assistant.AssistantAssetTraceRecorder;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.CapabilityPackService;
import com.orgmemory.core.assetregistry.PromptExecutionService;
import com.orgmemory.core.assetregistry.PromptRunCoordinator;
import com.orgmemory.core.assetregistry.PromptTemplateRenderer;
import com.orgmemory.core.assetregistry.WorkInstructionService;
import com.orgmemory.core.knowledge.CanonicalHybridKnowledgeSearch;
import com.orgmemory.core.knowledge.GraphRagKnowledgeRetrievalService;
import com.orgmemory.core.knowledge.PermissionAwareKnowledgeSearch;
import java.time.Clock;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AssistantProperties.class)
class AssistantConfiguration {

    @Bean
    Clock assistantClock() {
        return Clock.systemUTC();
    }

    @Bean
    ChatMemory assistantChatMemory(ChatMemoryRepository repository) {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(repository)
                .maxMessages(20)
                .build();
    }

    @Bean
    @Primary
    PermissionAwareKnowledgeSearch permissionAwareKnowledgeSearch(
            CanonicalHybridKnowledgeSearch canonicalRetrieval,
            ObjectProvider<GraphRagKnowledgeRetrievalService> graphRetrieval,
            AssistantProperties properties) {
        return switch (properties.retrievalEngine()) {
            case CANONICAL_HYBRID -> canonicalRetrieval;
            case GRAPH_RAG -> graphRetrieval.getIfAvailable(() -> {
                throw new IllegalStateException(
                        "Assistant retrieval engine GRAPH_RAG requires an EmbeddingModel and the GraphRAG query runtime");
            });
        };
    }

    @Bean
    AssistantService assistantService(
            PermissionAwareKnowledgeSearch retrieval,
            ChatModelPort chat) {
        return new AssistantService(retrieval, chat);
    }

    @Bean
    PromptExecutionService promptExecutionService(
            AssetRegistryService assets,
            PromptTemplateRenderer renderer,
            PermissionAwareKnowledgeSearch retrieval,
            ChatModelPort chat,
            AiRouteResolver routes,
            PromptRunCoordinator runs) {
        return new PromptExecutionService(
                assets, renderer, retrieval, chat, routes, runs);
    }

    @Bean
    AssistantAssetToolService assistantAssetToolService(
            AssetRegistryService assets,
            PromptExecutionService prompts,
            PromptTemplateRenderer renderer,
            WorkInstructionService instructions,
            CapabilityPackService packs,
            PermissionAwareKnowledgeSearch retrieval,
            AssistantAssetTraceRecorder traces) {
        return new AssistantAssetToolService(
                assets,
                prompts,
                renderer,
                instructions,
                packs,
                retrieval,
                traces);
    }
}
