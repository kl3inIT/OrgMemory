package com.orgmemory.api.assistant;

import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.GraphRagRetrievalPolicy;
import com.orgmemory.core.knowledge.KnowledgeEmbeddingProperties;
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
import com.orgmemory.integrations.ai.openai.OpenAiCompatibleChatModelProvider;
import com.orgmemory.integrations.graphrag.springai.JtokkitTextTokenizer;
import com.orgmemory.integrations.graphrag.springai.SpringAiKeywordPlanningModel;
import com.orgmemory.integrations.graphrag.springai.SpringAiQueryAnswerModel;
import com.orgmemory.integrations.graphrag.springai.SpringAiTextEmbeddingPort;
import java.util.List;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "orgmemory.graph-rag.query",
        name = "enabled",
        matchIfMissing = true)
@ConditionalOnBean(EmbeddingModel.class)
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
            OpenAiCompatibleChatModelProvider chatModels,
            AiRouteResolver routes,
            EmbeddingModel embeddingModel,
            KnowledgeEmbeddingProperties embedding,
            GraphRagQueryRuntimeProperties properties) {
        var projection = new StoreBackedAuthorizedQueryProjection(
                content,
                vectors,
                graph);
        var chatRoute = routes.resolve(AiWorkload.ASSISTANT_CHAT);
        var chatModel = chatModels.resolve(AiWorkload.ASSISTANT_CHAT);
        var keywordModel = new SpringAiKeywordPlanningModel(
                chatRoute.modelId(),
                chatModel);
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
                        properties.language()),
                embeddings,
                new JtokkitTextTokenizer(
                        properties.tokenizerEncoding()),
                unavailableReranker(),
                answerModel);
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
