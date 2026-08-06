package com.orgmemory.api.knowledge;

import com.orgmemory.core.knowledge.retrieval.KnowledgeEmbeddingProperties;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.chunking.SemanticEmbeddingInvocationException;
import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.query.CachingQueryEmbeddingService;
import com.orgmemory.graphrag.query.QueryEmbeddingCacheEventSink;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.integrations.graphrag.springai.SpringAiTextEmbeddingPort;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(QueryEmbeddingCacheProperties.class)
class QueryEmbeddingCacheConfiguration {

    @Bean
    @ConditionalOnMissingBean(QueryEmbeddingCacheEventSink.class)
    QueryEmbeddingCacheEventSink queryEmbeddingCacheEventSink(MeterRegistry meters) {
        return new MicrometerQueryEmbeddingCacheEventSink(meters);
    }

    @Bean
    @ConditionalOnMissingBean(CachingQueryEmbeddingService.class)
    CachingQueryEmbeddingService cachingQueryEmbeddingService(
            ObjectProvider<EmbeddingModel> models,
            KnowledgeEmbeddingProperties embedding,
            ObjectProvider<ModelInvocationCache> caches,
            QueryEmbeddingCacheProperties properties,
            QueryEmbeddingCacheEventSink events,
            ObjectProvider<Clock> clocks) {
        TextEmbeddingPort provider = lazyEmbeddingPort(models, embedding, properties);
        ModelInvocationCache cache =
                caches.getIfAvailable(NoOpModelInvocationCache::new);
        return new CachingQueryEmbeddingService(
                provider,
                cache,
                properties.timeToLive(),
                properties.maximumEntriesPerNamespace(),
                clocks.getIfAvailable(Clock::systemUTC),
                events);
    }

    @Bean
    @ConditionalOnMissingBean(QueryEmbeddingCacheJanitor.class)
    QueryEmbeddingCacheJanitor queryEmbeddingCacheJanitor(
            ObjectProvider<ModelInvocationCache> caches,
            QueryEmbeddingCacheProperties properties,
            QueryEmbeddingCacheEventSink events,
            ObjectProvider<Clock> clocks) {
        return new QueryEmbeddingCacheJanitor(
                caches.getIfAvailable(NoOpModelInvocationCache::new),
                properties,
                events,
                clocks.getIfAvailable(Clock::systemUTC));
    }

    private static TextEmbeddingPort lazyEmbeddingPort(
            ObjectProvider<EmbeddingModel> models,
            KnowledgeEmbeddingProperties embedding,
            QueryEmbeddingCacheProperties properties) {
        ProcessingComponentRef component = new ProcessingComponentRef(
                embedding.provider() + "-" + embedding.model(),
                "1");
        return new TextEmbeddingPort() {
            @Override
            public ProcessingComponentRef component() {
                return component;
            }

            @Override
            public List<FloatVector> embedAll(List<String> texts) {
                EmbeddingModel model = models.getIfAvailable();
                if (model == null) {
                    throw new SemanticEmbeddingInvocationException(
                            "semantic embedding model is unavailable",
                            new IllegalStateException("EmbeddingModel bean is unavailable"));
                }
                return new SpringAiTextEmbeddingPort(
                                model,
                                embedding.provider(),
                                embedding.model(),
                                properties.maximumBatchSize())
                        .embedAll(Objects.requireNonNull(texts, "texts"));
            }
        };
    }

    private static final class NoOpModelInvocationCache implements ModelInvocationCache {

        @Override
        public Optional<Entry> get(Key key, Instant now) {
            return Optional.empty();
        }

        @Override
        public void put(Key key, Entry entry) {
            // No persistence when the runtime has no cache adapter.
        }

        @Override
        public void invalidate(ProjectionNamespace namespace) {
            // Nothing to invalidate.
        }
    }
}
