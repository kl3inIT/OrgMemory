package com.orgmemory.api.assistant;

import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.GraphRagRetrievalPolicy;
import com.orgmemory.core.knowledge.retrieval.KnowledgeEmbeddingProperties;
import com.orgmemory.graphrag.cache.CanonicalCacheKeyHasher;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.query.ChunkReranker;
import com.orgmemory.graphrag.query.LightRagKeywordPlanner;
import com.orgmemory.graphrag.query.LightRagQueryEngine;
import com.orgmemory.graphrag.query.QueryAnswerModel;
import com.orgmemory.graphrag.query.StoreBackedAuthorizedQueryProjection;
import com.orgmemory.graphrag.storage.ContentStore;
import com.orgmemory.graphrag.storage.GraphStore;
import com.orgmemory.graphrag.storage.VectorIndex;
import com.orgmemory.integrations.ai.gateway.SpringAiChatModelProvider;
import com.orgmemory.integrations.graphrag.springai.JtokkitTextTokenizer;
import com.orgmemory.integrations.graphrag.springai.SpringAiKeywordPlanningModel;
import com.orgmemory.integrations.graphrag.springai.SpringAiQueryAnswerModel;
import com.orgmemory.integrations.graphrag.springai.SpringAiTextEmbeddingPort;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "orgmemory.assistant",
        name = "retrieval-engine",
        havingValue = "GRAPH_RAG",
        matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "orgmemory.graph-rag.query",
        name = "enabled",
        matchIfMissing = true)
@EnableConfigurationProperties(GraphRagQueryRuntimeProperties.class)
class GraphRagRuntimeConfiguration {

    @Bean
    GraphRagRetrievalPolicy graphRagRetrievalPolicy(
            GraphRagQueryRuntimeProperties properties) {
        return properties.toPolicy();
    }

    @Bean
    LightRagQueryEngine lightRagQueryEngine(
            ContentStore content,
            VectorIndex vectors,
            GraphStore graph,
            SpringAiChatModelProvider chatModels,
            AiRouteResolver routes,
            EmbeddingModel embeddingModel,
            KnowledgeEmbeddingProperties embedding,
            ModelInvocationCache modelInvocationCache,
            ObjectProvider<Clock> clocks,
            ObjectProvider<ChunkReranker> rerankers,
            GraphRagQueryRuntimeProperties properties) {
        var projection = new StoreBackedAuthorizedQueryProjection(
                content,
                vectors,
                graph);
        var chatRoute = routes.resolve(AiWorkload.ASSISTANT_CHAT);
        var chatModel = chatModels.resolve(AiWorkload.ASSISTANT_CHAT);
        var keywordRoute = routes.resolve(AiWorkload.KEYWORD_PLANNING);
        var keywordChatModel =
                chatModels.resolve(AiWorkload.KEYWORD_PLANNING);
        var keywordModel = new SpringAiKeywordPlanningModel(
                keywordRoute.modelId(),
                keywordChatModel);
        String keywordRouteFingerprint = CanonicalCacheKeyHasher.sha256(
                "orgmemory.ai.keyword-route.v1",
                Map.of(
                        "gatewayId", keywordRoute.gatewayId(),
                        "modelId", keywordRoute.modelId()));
        TextEmbeddingPort embeddings = new SpringAiTextEmbeddingPort(
                embeddingModel,
                embedding.provider(),
                embedding.model(),
                properties.maximumEmbeddingBatchSize());
        QueryAnswerModel answerModel = new SpringAiQueryAnswerModel(
                chatRoute.modelId(),
                chatModel);
        return new LightRagQueryEngine(
                projection,
                new LightRagKeywordPlanner(
                        keywordModel,
                        properties.language(),
                        new LightRagKeywordPlanner.CachePolicy(
                                modelInvocationCache,
                                keywordRouteFingerprint,
                                properties.keywordCacheTtl(),
                                clocks.getIfAvailable(Clock::systemUTC))),
                embeddings,
                new JtokkitTextTokenizer(
                        properties.tokenizerEncoding()),
                configuredReranker(rerankers, properties),
                answerModel);
    }

    private static ChunkReranker configuredReranker(
            ObjectProvider<ChunkReranker> rerankers,
            GraphRagQueryRuntimeProperties properties) {
        if (!properties.rerankEnabled()) {
            return unavailableReranker();
        }
        ChunkReranker configured = rerankers.getIfAvailable();
        if (configured == null) {
            throw new IllegalStateException(
                    "GraphRAG reranking is enabled for provider '"
                            + properties.rerankProvider()
                            + "' but no ChunkReranker adapter is configured");
        }
        return configured;
    }

    private static ChunkReranker unavailableReranker() {
        return new ChunkReranker() {
            @Override
            public ProcessingComponentRef component() {
                return new ProcessingComponentRef(
                        "reranker-disabled",
                        "1");
            }

            @Override
            public List<Score> rerank(
                    String query,
                    List<Candidate> candidates,
                    int limit) {
                throw new UnsupportedOperationException(
                        "A reranker provider is not configured");
            }
        };
    }
}
