package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LightRagKeywordPlannerCacheTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-28T00:00:00Z"),
            ZoneOffset.UTC);

    @Test
    void exactQueryReusesCachedModelPlanWithinTheOrganization() {
        RecordingModel model = new RecordingModel();
        InMemoryCache cache = new InMemoryCache();
        LightRagKeywordPlanner planner = planner(model, cache, "a".repeat(64));

        LightRagKeywordPlanner.PlanningResult first = planner.planWithTrace(
                "What is the leave policy?",
                null,
                ORGANIZATION_ID,
                "MIX");
        LightRagKeywordPlanner.PlanningResult second = planner.planWithTrace(
                "What is the leave policy?",
                null,
                ORGANIZATION_ID,
                "MIX");

        assertEquals(first.plan(), second.plan());
        assertEquals(GraphRagEventSink.CacheStatus.MISS, first.cacheStatus());
        assertEquals(GraphRagEventSink.CacheStatus.HIT, second.cacheStatus());
        assertEquals(1, model.calls);
        assertEquals(1, cache.entries.size());
    }

    @Test
    void organizationStrategyQueryAndRouteRemainIndependentCacheDimensions() {
        RecordingModel model = new RecordingModel();
        InMemoryCache cache = new InMemoryCache();
        LightRagKeywordPlanner firstRoute =
                planner(model, cache, "a".repeat(64));
        LightRagKeywordPlanner secondRoute =
                planner(model, cache, "b".repeat(64));
        LightRagKeywordPlanner secondLanguage =
                planner(
                        model,
                        cache,
                        "a".repeat(64),
                        "English");

        firstRoute.plan("leave policy", null, ORGANIZATION_ID, "MIX");
        firstRoute.plan(
                "leave policy",
                null,
                UUID.fromString("10000000-0000-4000-8000-000000000002"),
                "MIX");
        firstRoute.plan("leave policy", null, ORGANIZATION_ID, "LOCAL");
        firstRoute.plan("expense policy", null, ORGANIZATION_ID, "MIX");
        secondRoute.plan("leave policy", null, ORGANIZATION_ID, "MIX");
        secondLanguage.plan(
                "leave policy",
                null,
                ORGANIZATION_ID,
                "MIX");

        assertEquals(6, model.calls);
        assertEquals(6, cache.entries.size());
    }

    @Test
    void trustedKeywordsBypassBothProviderAndCache() {
        RecordingModel model = new RecordingModel();
        InMemoryCache cache = new InMemoryCache();
        LightRagKeywordPlanner planner = planner(model, cache, "a".repeat(64));

        KeywordPlan result = planner.plan(
                "leave policy",
                KeywordPlan.trusted(
                        List.of("policy"),
                        List.of("leave")),
                ORGANIZATION_ID,
                "MIX");

        assertEquals(KeywordPlan.Source.TRUSTED_CALLER, result.source());
        assertEquals(0, model.calls);
        assertEquals(Map.of(), cache.entries);
    }

    private static LightRagKeywordPlanner planner(
            RecordingModel model,
            InMemoryCache cache,
            String routeFingerprint) {
        return planner(
                model,
                cache,
                routeFingerprint,
                "Vietnamese");
    }

    private static LightRagKeywordPlanner planner(
            RecordingModel model,
            InMemoryCache cache,
            String routeFingerprint,
            String language) {
        return new LightRagKeywordPlanner(
                model,
                language,
                new LightRagKeywordPlanner.CachePolicy(
                        cache,
                        routeFingerprint,
                        Duration.ofHours(24),
                        CLOCK));
    }

    private static final class RecordingModel
            implements KeywordPlanningModel {

        private int calls;

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef(
                    "keyword-cache-test",
                    "1");
        }

        @Override
        public KeywordPlan complete(String prompt) {
            calls++;
            return KeywordPlan.model(
                    List.of("policy"),
                    List.of("leave"));
        }
    }

    private static final class InMemoryCache
            implements ModelInvocationCache {

        private final Map<Key, Entry> entries = new LinkedHashMap<>();

        @Override
        public Optional<Entry> get(Key key, Instant now) {
            return Optional.ofNullable(entries.get(key))
                    .filter(entry -> !entry.expiredAt(now));
        }

        @Override
        public void put(Key key, Entry entry) {
            entries.put(key, entry);
        }

        @Override
        public void invalidate(ProjectionNamespace namespace) {
            entries.keySet().removeIf(
                    key -> key.namespace().equals(namespace));
        }
    }
}
