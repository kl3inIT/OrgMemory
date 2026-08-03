package com.orgmemory.api.assistant;

import com.orgmemory.core.ai.ChatModelPort;
import com.orgmemory.core.assistant.AssistantAssetToolService;
import com.orgmemory.core.assistant.AssistantAssetTraceRecorder;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assistant.observability.AssistantTurnEvent;
import com.orgmemory.core.assistant.observability.AssistantTurnMeterObservationHandler;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.CapabilityPackService;
import com.orgmemory.core.assetregistry.workinstructioncontract.WorkInstructionOperations;
import com.orgmemory.core.assetregistry.promptcontract.PromptAssistantOperations;
import com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearch;
import com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService;
import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.ObservationRegistry;
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
            ChatModelPort chat,
            ObservationRegistry observations,
            AssistantProperties properties) {
        return new AssistantService(
                retrieval, chat, observations, observedEngine(properties));
    }

    /**
     * Names the engine for telemetry from the same property that selects it, so the tag cannot
     * disagree with the bean above. Asking {@code PermissionAwareKnowledgeSearch} which
     * implementation it is would put a telemetry concern into an interface that exists to keep
     * the assistant engine-neutral.
     */
    private static AssistantTurnEvent.RetrievalEngine observedEngine(
            AssistantProperties properties) {
        return switch (properties.retrievalEngine()) {
            case GRAPH_RAG -> AssistantTurnEvent.RetrievalEngine.GRAPH_RAG;
            case CANONICAL_HYBRID -> AssistantTurnEvent.RetrievalEngine.CANONICAL_HYBRID;
        };
    }

    /**
     * Unconditional, and injecting the registry directly, on purpose.
     *
     * <p>This carried {@code @ConditionalOnBean(MeterRegistry.class)} to degrade rather than
     * fail where metrics were absent. It degraded always. Bean conditions are only meaningful
     * inside {@code @AutoConfiguration}, which Boot processes after user configuration; this is
     * user configuration, so the condition ran before {@code MetricsAutoConfiguration} had
     * contributed the registry, found nothing, and dropped the handler on every startup. The
     * turn still produced a span and the framework's own timer, so nothing looked broken —
     * {@code orgmemory.assistant.time_to_first_token} simply never existed, which is how it
     * reached production unnoticed. {@code GraphRagObservabilityAutoConfiguration} states the
     * rule this violated.
     *
     * <p>Requiring the registry is the point rather than a compromise: a missing one now stops
     * the context at startup instead of quietly removing a metric an operator is relying on.
     * Silent degradation of telemetry is the failure this increment exists to remove.
     */
    @Bean
    AssistantTurnMeterObservationHandler assistantTurnMeterObservationHandler(
            MeterRegistry meters) {
        return new AssistantTurnMeterObservationHandler(meters);
    }

    @Bean
    AssistantAssetToolService assistantAssetToolService(
            AssetRegistryService assets,
            PromptAssistantOperations prompts,
            WorkInstructionOperations instructions,
            CapabilityPackService packs,
            PermissionAwareKnowledgeSearch retrieval,
            AssistantAssetTraceRecorder traces) {
        return new AssistantAssetToolService(
                assets,
                prompts,
                instructions,
                packs,
                retrieval,
                traces);
    }
}
