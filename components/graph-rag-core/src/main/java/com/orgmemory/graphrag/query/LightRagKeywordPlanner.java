package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.cache.CanonicalCacheKeyHasher;
import com.orgmemory.graphrag.cache.ModelInvocationCache;
import com.orgmemory.graphrag.cache.ModelInvocationCacheKeys;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Executable LightRAG keyword planning semantics.
 *
 * <p>The model only completes the prompt. Prompt ownership, trusted bypass,
 * normalization, and the short-query fallback remain in the pure-Java core.
 */
public final class LightRagKeywordPlanner {

    private static final int SHORT_QUERY_FALLBACK_LIMIT = 50;
    private static final String CACHE_MEDIA_TYPE =
            "application/vnd.orgmemory.keyword-plan.v1+text";

    private final KeywordPlanningModel model;
    private final String language;
    private final CachePolicy cachePolicy;

    public LightRagKeywordPlanner(KeywordPlanningModel model, String language) {
        this(model, language, null);
    }

    public LightRagKeywordPlanner(
            KeywordPlanningModel model,
            String language,
            CachePolicy cachePolicy) {
        this.model = Objects.requireNonNull(model, "model");
        this.language = requireText(language, "language");
        this.cachePolicy = cachePolicy;
    }

    public KeywordPlan plan(String query, KeywordPlan trustedKeywords) {
        return plan(query, trustedKeywords, null, "unspecified");
    }

    public KeywordPlan plan(
            String query,
            KeywordPlan trustedKeywords,
            UUID organizationId,
            String strategy) {
        return planWithTrace(
                        query,
                        trustedKeywords,
                        organizationId,
                        strategy)
                .plan();
    }

    public PlanningResult planWithTrace(
            String query,
            KeywordPlan trustedKeywords,
            UUID organizationId,
            String strategy) {
        String normalizedQuery = requireText(query, "query");
        PlanningResult planning = trustedKeywords == null
                ? modelPlan(
                        normalizedQuery,
                        organizationId,
                        requireText(strategy, "strategy"))
                : new PlanningResult(
                        new KeywordPlan(
                                trustedKeywords.highLevel(),
                                trustedKeywords.lowLevel(),
                                KeywordPlan.Source.TRUSTED_CALLER),
                        GraphRagEventSink.CacheStatus.BYPASS,
                        null);
        KeywordPlan candidate = planning.plan();
        Objects.requireNonNull(candidate, "keyword plan");
        if (!candidate.empty()) {
            return planning;
        }
        if (trustedKeywords == null
                && normalizedQuery.length() < SHORT_QUERY_FALLBACK_LIMIT) {
            return new PlanningResult(
                    new KeywordPlan(
                            List.of(),
                            List.of(normalizedQuery),
                            KeywordPlan.Source.SHORT_QUERY_FALLBACK),
                    planning.cacheStatus(),
                    planning.modelRouteFingerprint());
        }
        return new PlanningResult(
                KeywordPlan.empty(candidate.source()),
                planning.cacheStatus(),
                planning.modelRouteFingerprint());
    }

    private PlanningResult modelPlan(
            String normalizedQuery,
            UUID organizationId,
            String strategy) {
        KeywordPlanningModel.Invocation invocation = model.resolve(organizationId);
        String routeFingerprint = invocation.modelRouteFingerprint();
        ModelInvocationCache.Key key = cacheKey(
                normalizedQuery,
                organizationId,
                strategy,
                routeFingerprint);
        if (key != null) {
            Optional<KeywordPlan> cached = cached(key);
            if (cached.isPresent()) {
                return new PlanningResult(
                        cached.orElseThrow(),
                        GraphRagEventSink.CacheStatus.HIT,
                        routeFingerprint);
            }
        }
        KeywordPlan generated =
                Objects.requireNonNull(
                        invocation.complete(prompt(normalizedQuery, language)),
                        "keyword plan");
        if (key != null) {
            cache(key, generated);
        }
        return new PlanningResult(
                generated,
                GraphRagEventSink.CacheStatus.MISS,
                routeFingerprint);
    }

    private ModelInvocationCache.Key cacheKey(
            String normalizedQuery,
            UUID organizationId,
            String strategy,
            String modelRouteFingerprint) {
        if (cachePolicy == null || organizationId == null) {
            return null;
        }
        return ModelInvocationCacheKeys.keywords(
                new ProjectionNamespace(
                        organizationId,
                        "lightrag-query",
                        "keywords"),
                normalizedQuery,
                language,
                strategy,
                CanonicalCacheKeyHasher.requireSha256(
                        modelRouteFingerprint,
                        "modelRouteFingerprint"),
                cachePolicy.profileFingerprint(language));
    }

    private Optional<KeywordPlan> cached(ModelInvocationCache.Key key) {
        try {
            return cachePolicy.cache()
                    .get(key, cachePolicy.clock().instant())
                    .filter(entry -> CACHE_MEDIA_TYPE.equals(entry.mediaType()))
                    .flatMap(entry -> decode(entry.payload()));
        } catch (RuntimeException ignoredCacheFailure) {
            return Optional.empty();
        }
    }

    private void cache(
            ModelInvocationCache.Key key,
            KeywordPlan plan) {
        Instant createdAt = cachePolicy.clock().instant();
        try {
            cachePolicy.cache().put(
                    key,
                    new ModelInvocationCache.Entry(
                            CACHE_MEDIA_TYPE,
                            encode(plan),
                            createdAt,
                            createdAt.plus(cachePolicy.ttl())));
        } catch (RuntimeException ignoredCacheFailure) {
            // Exact derived cache availability cannot block query planning.
        }
    }

    private static String encode(KeywordPlan plan) {
        List<String> lines = new ArrayList<>();
        plan.highLevel().forEach(value ->
                lines.add("H:" + encodeValue(value)));
        plan.lowLevel().forEach(value ->
                lines.add("L:" + encodeValue(value)));
        return String.join("\n", lines);
    }

    private static Optional<KeywordPlan> decode(String payload) {
        try {
            List<String> high = new ArrayList<>();
            List<String> low = new ArrayList<>();
            if (!payload.isEmpty()) {
                for (String line : payload.split("\\n", -1)) {
                    if (line.length() < 3 || line.charAt(1) != ':') {
                        return Optional.empty();
                    }
                    String value = decodeValue(line.substring(2));
                    if (line.charAt(0) == 'H') {
                        high.add(value);
                    } else if (line.charAt(0) == 'L') {
                        low.add(value);
                    } else {
                        return Optional.empty();
                    }
                }
            }
            return Optional.of(KeywordPlan.model(high, low));
        } catch (IllegalArgumentException malformedPayload) {
            return Optional.empty();
        }
    }

    private static String encodeValue(String value) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String decodeValue(String value) {
        String decoded = new String(
                Base64.getUrlDecoder().decode(value),
                StandardCharsets.UTF_8);
        return requireText(decoded, "cached keyword");
    }

    static String prompt(String query, String language) {
        return """
                ---Role---
                You extract search keywords for a retrieval-augmented generation system.

                ---Goal---
                Return high-level concepts and low-level concrete entities from the user query.

                ---Constraints---
                - Return one JSON object and no other text.
                - Use exactly the keys "high_level_keywords" and "low_level_keywords".
                - Both values are arrays of concise strings.
                - Derive every keyword only from the user query.
                - Keep meaningful multi-word phrases intact and remove duplicates.
                - Write keywords in %s; preserve proper nouns in their original language.
                - For vague or nonsensical input, return both arrays empty.

                ---User Query---
                %s
                """.formatted(language, query);
    }

    private static String requireText(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    public record CachePolicy(
            ModelInvocationCache cache,
            Duration ttl,
            Clock clock) {

        public CachePolicy {
            Objects.requireNonNull(cache, "cache");
            Objects.requireNonNull(ttl, "ttl");
            if (ttl.isZero()
                    || ttl.isNegative()
                    || ttl.compareTo(Duration.ofDays(30)) > 0) {
                throw new IllegalArgumentException(
                        "keyword cache ttl must be between 1 ns and 30 days");
            }
            Objects.requireNonNull(clock, "clock");
        }

        private String profileFingerprint(String language) {
            return CanonicalCacheKeyHasher.sha256(
                    "orgmemory.lightrag.keyword-profile.v1",
                    Map.of(
                            "language", language,
                            "promptVersion", "1"));
        }
    }

    public record PlanningResult(
            KeywordPlan plan,
            GraphRagEventSink.CacheStatus cacheStatus,
            String modelRouteFingerprint) {

        public PlanningResult {
            Objects.requireNonNull(plan, "plan");
            Objects.requireNonNull(cacheStatus, "cacheStatus");
            modelRouteFingerprint = modelRouteFingerprint == null
                    ? null
                    : CanonicalCacheKeyHasher.requireSha256(
                            modelRouteFingerprint,
                            "modelRouteFingerprint");
        }
    }
}
