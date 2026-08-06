package com.orgmemory.api.knowledge;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileSpec;
import com.orgmemory.core.knowledge.retrieval.KnowledgeEmbeddingProperties;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.query.CachingQueryEmbeddingService;
import com.orgmemory.graphrag.query.QueryEmbeddingCacheEventSink;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class QueryEmbeddingCacheWiringTests {

    private static final ModelInvocationCache AUTO_CONFIGURED_CACHE =
            new MapModelInvocationCache();

    @Test
    void deferredPersistenceAutoConfigurationIsNotPreemptedByFallbackWiring() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TestPersistenceAutoConfiguration.class))
                .withUserConfiguration(
                        QueryEmbeddingCacheConfiguration.class,
                        TestDependencies.class)
                .run(context -> assertSame(
                        AUTO_CONFIGURED_CACHE,
                        context.getBean(ModelInvocationCache.class)));
    }

    @Test
    void missingPersistenceAdapterFallsBackWithoutPublishingANoOpBean() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        QueryEmbeddingCacheConfiguration.class,
                        TestDependencies.class)
                .run(context -> {
                    assertEquals(0, context.getBeanNamesForType(ModelInvocationCache.class).length);
                    context.getBean(CachingQueryEmbeddingService.class);
                    context.getBean(QueryEmbeddingCacheJanitor.class);
                });
    }

    @Test
    void canonicalAdapterReusesTheSharedExactCache() {
        UUID organizationId = UUID.randomUUID();
        UUID profileId = UUID.randomUUID();
        AtomicInteger providerCalls = new AtomicInteger();
        TextEmbeddingPort provider = new TextEmbeddingPort() {
            @Override
            public ProcessingComponentRef component() {
                return new ProcessingComponentRef("openai-text-embedding-3-large", "1");
            }

            @Override
            public List<FloatVector> embedAll(List<String> texts) {
                providerCalls.incrementAndGet();
                return texts.stream()
                        .map(text -> new FloatVector(new float[] {text.length(), 1.0f}))
                        .toList();
            }
        };
        CachingQueryEmbeddingService cache = new CachingQueryEmbeddingService(
                provider,
                new MapModelInvocationCache(),
                Duration.ofDays(7),
                Clock.fixed(Instant.parse("2026-08-06T00:00:00Z"), ZoneOffset.UTC));
        EmbeddingProfileRegistry profiles = mock(EmbeddingProfileRegistry.class);
        when(profiles.find(eq(organizationId), any(EmbeddingProfileSpec.class)))
                .thenReturn(Optional.of(new EmbeddingProfileRef(
                        profileId,
                        organizationId,
                        "profile",
                        "openai",
                        "text-embedding-3-large",
                        2,
                        EmbeddingDistanceMetric.COSINE)));
        AiRouteResolver routes = mock(AiRouteResolver.class);
        when(routes.resolve(AiWorkload.QUERY_EMBEDDING))
                .thenReturn(new AiRoute("openai", "text-embedding-3-large"));
        SpringAiQueryEmbeddingAdapter adapter = new SpringAiQueryEmbeddingAdapter(
                cache,
                profiles,
                new KnowledgeEmbeddingProperties("openai", "text-embedding-3-large", 2),
                routes);

        var first = adapter.embed(organizationId, "leave policy").orElseThrow();
        var second = adapter.embed(organizationId, "leave policy").orElseThrow();

        assertArrayEquals(first.vector(), second.vector());
        assertEquals(1, providerCalls.get());
    }

    @Test
    void propertiesAndMetricsRemainBounded() {
        QueryEmbeddingCacheProperties defaults =
                new QueryEmbeddingCacheProperties(null, null, null, null, null);
        assertEquals(Duration.ofDays(7), defaults.timeToLive());
        assertEquals(10_000, defaults.maximumEntriesPerNamespace());
        assertEquals(1_000, defaults.cleanupBatchSize());
        assertEquals(Duration.ofMinutes(15), defaults.cleanupInterval());
        assertThrows(
                IllegalArgumentException.class,
                () -> new QueryEmbeddingCacheProperties(
                        Duration.ofNanos(1), null, null, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QueryEmbeddingCacheProperties(
                        Duration.ofDays(31), null, null, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new QueryEmbeddingCacheProperties(null, 1_000_001, null, null, null));

        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        new MicrometerQueryEmbeddingCacheEventSink(meters).emit(
                new QueryEmbeddingCacheEventSink.Event(
                        QueryEmbeddingCacheEventSink.Outcome.HIT,
                        Duration.ofMillis(2),
                        3));

        assertEquals(
                1L,
                meters.get(MicrometerQueryEmbeddingCacheEventSink.DURATION)
                        .tag("outcome", "hit")
                        .timer()
                        .count());
        assertEquals(
                3.0,
                meters.get(MicrometerQueryEmbeddingCacheEventSink.ITEMS)
                        .tag("outcome", "hit")
                        .counter()
                        .count());
    }

    @Test
    void janitorDeletesOnlyABoundedBatchOfExpiredQueryEmbeddings() {
        Instant now = Instant.parse("2026-08-06T00:00:00Z");
        ModelInvocationCache cache = mock(ModelInvocationCache.class);
        QueryEmbeddingCacheEventSink events = mock(QueryEmbeddingCacheEventSink.class);
        QueryEmbeddingCacheProperties properties =
                new QueryEmbeddingCacheProperties(null, null, null, 250, null);
        QueryEmbeddingCacheJanitor janitor = new QueryEmbeddingCacheJanitor(
                cache,
                properties,
                events,
                Clock.fixed(now, ZoneOffset.UTC));

        janitor.deleteExpired();

        verify(cache).deleteExpired("QUERY_EMBEDDING", now, 250);
    }

    @AutoConfiguration
    static class TestPersistenceAutoConfiguration {

        @Bean
        @ConditionalOnMissingBean(ModelInvocationCache.class)
        ModelInvocationCache modelInvocationCache() {
            return AUTO_CONFIGURED_CACHE;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestDependencies {

        @Bean
        KnowledgeEmbeddingProperties knowledgeEmbeddingProperties() {
            return new KnowledgeEmbeddingProperties(
                    "openai", "text-embedding-3-large", 1536);
        }

        @Bean
        SimpleMeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    private static final class MapModelInvocationCache implements ModelInvocationCache {
        private final Map<Key, Entry> entries = new HashMap<>();

        @Override
        public synchronized Optional<Entry> get(Key key, Instant now) {
            Entry entry = entries.get(key);
            return entry == null || entry.expiredAt(now)
                    ? Optional.empty()
                    : Optional.of(entry);
        }

        @Override
        public synchronized void put(Key key, Entry entry) {
            entries.put(key, entry);
        }

        @Override
        public synchronized void putBounded(
                ProjectionNamespace namespace,
                String operation,
                Map<Key, Entry> boundedEntries,
                Instant now,
                int maximumEntries) {
            entries.putAll(boundedEntries);
            entries.entrySet().removeIf(entry ->
                    entry.getKey().namespace().equals(namespace)
                            && entry.getKey().operation().equals(operation)
                            && entry.getValue().expiredAt(now));
            entries.entrySet().stream()
                    .filter(entry -> entry.getKey().namespace().equals(namespace))
                    .filter(entry -> entry.getKey().operation().equals(operation))
                    .sorted((left, right) -> right.getValue()
                            .createdAt()
                            .compareTo(left.getValue().createdAt()))
                    .skip(maximumEntries)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(entries::remove);
        }

        @Override
        public synchronized void invalidate(ProjectionNamespace namespace) {
            entries.keySet().removeIf(key -> key.namespace().equals(namespace));
        }
    }
}
