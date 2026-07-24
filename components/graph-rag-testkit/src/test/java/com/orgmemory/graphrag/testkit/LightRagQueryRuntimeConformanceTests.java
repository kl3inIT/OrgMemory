package com.orgmemory.graphrag.testkit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.authorization.AuthorizedEvidenceScope;
import com.orgmemory.graphrag.chunking.EncodedText;
import com.orgmemory.graphrag.chunking.TextEmbeddingPort;
import com.orgmemory.graphrag.chunking.TextTokenizer;
import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.CanonicalRelation;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.model.RelationContribution;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.query.AuthorizedQueryProjection;
import com.orgmemory.graphrag.query.ChunkReranker;
import com.orgmemory.graphrag.query.KeywordPlan;
import com.orgmemory.graphrag.query.KeywordPlanningModel;
import com.orgmemory.graphrag.query.LightRagKeywordPlanner;
import com.orgmemory.graphrag.query.LightRagQueryEngine;
import com.orgmemory.graphrag.query.LightRagQueryMode;
import com.orgmemory.graphrag.query.LightRagQueryRequest;
import com.orgmemory.graphrag.query.LightRagQueryResult;
import com.orgmemory.graphrag.query.QueryAnswerModel;
import com.orgmemory.graphrag.query.QueryOutputMode;
import com.orgmemory.graphrag.query.SecureContextBudget;
import com.orgmemory.graphrag.storage.ProjectionKind;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import com.orgmemory.graphrag.storage.ProjectionSnapshot;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LightRagQueryRuntimeConformanceTests {

    private static final UUID ORGANIZATION_ID = id("organization");
    private static final UUID OTHER_ORGANIZATION_ID = id("other-organization");
    private static final UUID ACTOR_ID = id("actor");
    private static final UUID ALLOWED_ASSET_ID = id("allowed-asset");
    private static final UUID RESTRICTED_ASSET_ID = id("restricted-asset");
    private static final UUID EMBEDDING_PROFILE_ID = id("embedding-profile");
    private static final UUID FIRST_ENTITY_ID = id("first-entity");
    private static final UUID SECOND_ENTITY_ID = id("second-entity");
    private static final UUID RELATION_ID = id("relation");
    private static final UUID FIRST_CHUNK_ID = id("first-chunk");
    private static final UUID SECOND_CHUNK_ID = id("second-chunk");
    private static final UUID RELATION_CHUNK_ID = id("relation-chunk");
    private static final UUID VECTOR_CHUNK_ID = id("vector-chunk");
    private static final UUID RESTRICTED_CHUNK_ID = id("restricted-chunk");
    private static final UUID CROSS_TENANT_CHUNK_ID = id("cross-tenant-chunk");
    private static final long GENERATION = 7;

    private InMemoryAuthorizedQueryProjection projection;
    private RecordingKeywordModel keywordModel;
    private RecordingEmbeddings embeddings;
    private ConfigurableReranker reranker;
    private RecordingAnswerModel answerModel;
    private LightRagQueryEngine engine;

    @BeforeEach
    void setUp() {
        projection = new InMemoryAuthorizedQueryProjection();
        keywordModel = new RecordingKeywordModel(new KeywordPlan(
                List.of("workplace policy"),
                List.of("probation"),
                KeywordPlan.Source.MODEL));
        embeddings = new RecordingEmbeddings();
        reranker = new ConfigurableReranker();
        answerModel = new RecordingAnswerModel();
        engine = new LightRagQueryEngine(
                projection,
                new LightRagKeywordPlanner(keywordModel, "Vietnamese"),
                embeddings,
                new WordTokenizer(),
                reranker,
                answerModel);
        seedProjection();
    }

    @Test
    void everyLightRagModeExecutesItsDefinedRetrievalBranches() {
        Map<LightRagQueryMode, ExpectedBranches> expectations = new LinkedHashMap<>();
        expectations.put(LightRagQueryMode.LOCAL, new ExpectedBranches(true, false, false));
        expectations.put(LightRagQueryMode.GLOBAL, new ExpectedBranches(false, true, false));
        expectations.put(LightRagQueryMode.HYBRID, new ExpectedBranches(true, true, false));
        expectations.put(LightRagQueryMode.NAIVE, new ExpectedBranches(false, false, true));
        expectations.put(LightRagQueryMode.MIX, new ExpectedBranches(true, true, true));

        for (Map.Entry<LightRagQueryMode, ExpectedBranches> entry : expectations.entrySet()) {
            LightRagQueryResult result = engine.execute(request(
                    entry.getKey(),
                    QueryOutputMode.CONTEXT,
                    false,
                    true,
                    false,
                    entry.getKey().usesGraph() ? trustedKeywords() : null));

            assertEquals(LightRagQueryResult.Status.SUCCESS, result.status(), entry.getKey().name());
            assertEquals(
                    entry.getValue().entities(),
                    result.trace().entitySeedCount() > 0,
                    entry.getKey().name());
            assertEquals(
                    entry.getValue().relations(),
                    result.trace().relationSeedCount() > 0,
                    entry.getKey().name());
            assertEquals(
                    entry.getValue().vectorChunks(),
                    result.trace().vectorChunkCount() > 0,
                    entry.getKey().name());
        }
    }

    @Test
    void mixBatchesRawQueryLowKeywordsAndHighKeywordsExactlyOnce() {
        LightRagQueryResult result = engine.execute(request(
                LightRagQueryMode.MIX,
                QueryOutputMode.CONTEXT,
                false,
                true,
                false,
                trustedKeywords()));

        assertEquals(1, embeddings.batches.size());
        assertEquals(
                List.of("What is the probation policy?", "probation", "workplace policy"),
                embeddings.batches.getFirst());
        assertEquals(embeddings.batches.getFirst(), result.trace().embeddingInputs());
        assertEquals(0, keywordModel.calls);
        assertEquals(KeywordPlan.Source.TRUSTED_CALLER, result.trace().keywords().source());
        assertTrue(result.trace().chunkSignals().stream()
                .anyMatch(signal -> signal.origin() == LightRagQueryResult.Origin.VECTOR));
        assertTrue(result.trace().chunkSignals().stream()
                .anyMatch(signal -> signal.origin() == LightRagQueryResult.Origin.ENTITY));
        assertTrue(result.trace().chunkSignals().stream()
                .anyMatch(signal -> signal.origin() == LightRagQueryResult.Origin.RELATION));
    }

    @Test
    void bypassPerformsNoRetrievalPlanningEmbeddingOrProjectionReads() {
        LightRagQueryResult result = engine.execute(request(
                LightRagQueryMode.BYPASS,
                QueryOutputMode.ANSWER,
                false,
                false,
                true,
                null));

        assertEquals(0, projection.reads());
        assertEquals(0, keywordModel.calls);
        assertTrue(embeddings.batches.isEmpty());
        assertEquals(1, answerModel.requests.size());
        assertTrue(answerModel.requests.getFirst().streaming());
        LightRagQueryResult.StreamingAnswer answer =
                assertInstanceOf(LightRagQueryResult.StreamingAnswer.class, result.answer());
        assertEquals(List.of("direct ", "answer"), iteratorValues(answer.chunks()));
        assertTrue(result.references().isEmpty());
    }

    @Test
    void projectionNeverReturnsRestrictedOrCrossTenantEvidence() {
        LightRagQueryResult result = engine.execute(request(
                LightRagQueryMode.MIX,
                QueryOutputMode.CONTEXT,
                false,
                true,
                false,
                trustedKeywords()));

        assertFalse(result.references().isEmpty());
        assertTrue(result.references().stream().allMatch(reference ->
                reference.evidence().organizationId().equals(ORGANIZATION_ID)
                        && reference.evidence().knowledgeAssetId().equals(ALLOWED_ASSET_ID)));
        assertTrue(result.references().stream().noneMatch(reference ->
                reference.evidence().chunkId().equals(RESTRICTED_CHUNK_ID)
                        || reference.evidence().chunkId().equals(CROSS_TENANT_CHUNK_ID)));
        assertFalse(result.context().contains("restricted"));
        assertFalse(result.context().contains("cross tenant"));
    }

    @Test
    void rerankThresholdAppliesAfterRerankingAndProviderFailureFailsOpen() {
        reranker.scores = Map.of(
                VECTOR_CHUNK_ID, 0.2,
                FIRST_CHUNK_ID, 0.95,
                SECOND_CHUNK_ID, 0.85,
                RELATION_CHUNK_ID, 0.75);
        LightRagQueryResult filtered = engine.execute(request(
                LightRagQueryMode.MIX,
                QueryOutputMode.CONTEXT,
                true,
                true,
                false,
                trustedKeywords()));

        assertTrue(filtered.trace().rerankAttempted());
        assertFalse(filtered.trace().rerankFallback());
        assertTrue(filtered.references().stream().noneMatch(reference ->
                reference.evidence().chunkId().equals(VECTOR_CHUNK_ID)));

        reranker.failure = new IllegalStateException("provider unavailable");
        LightRagQueryResult fallback = engine.execute(request(
                LightRagQueryMode.MIX,
                QueryOutputMode.CONTEXT,
                true,
                true,
                false,
                trustedKeywords()));

        assertTrue(fallback.trace().rerankAttempted());
        assertTrue(fallback.trace().rerankFallback());
        assertFalse(fallback.references().isEmpty());
    }

    @Test
    void outputModesAndHeadingPolicyAreExplicit() {
        LightRagQueryResult contextOnly = engine.execute(request(
                LightRagQueryMode.NAIVE,
                QueryOutputMode.CONTEXT,
                false,
                false,
                false,
                null));
        assertFalse(contextOnly.context().contains("Employee handbook / Probation"));
        assertTrue(contextOnly.prompt().isEmpty());
        assertInstanceOf(LightRagQueryResult.NoAnswer.class, contextOnly.answer());

        LightRagQueryResult promptOnly = engine.execute(request(
                LightRagQueryMode.NAIVE,
                QueryOutputMode.PROMPT,
                false,
                true,
                false,
                null));
        assertTrue(promptOnly.context().contains("Employee handbook / Probation"));
        assertTrue(promptOnly.prompt().contains("---User Query---"));
        assertInstanceOf(LightRagQueryResult.NoAnswer.class, promptOnly.answer());

        LightRagQueryResult complete = engine.execute(request(
                LightRagQueryMode.NAIVE,
                QueryOutputMode.ANSWER,
                false,
                true,
                false,
                null));
        assertInstanceOf(LightRagQueryResult.CompleteAnswer.class, complete.answer());
        assertEquals(1, answerModel.requests.size());
    }

    @Test
    void invalidBypassAndStreamingCombinationsFailAtTheRequestBoundary() {
        assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        LightRagQueryMode.BYPASS,
                        QueryOutputMode.CONTEXT,
                        false,
                        false,
                        false,
                        null));
        assertThrows(
                IllegalArgumentException.class,
                () -> request(
                        LightRagQueryMode.NAIVE,
                        QueryOutputMode.CONTEXT,
                        false,
                        false,
                        true,
                        null));
    }

    private void seedProjection() {
        CanonicalEntity first = new CanonicalEntity(FIRST_ENTITY_ID, "Probation policy");
        CanonicalEntity second = new CanonicalEntity(SECOND_ENTITY_ID, "Employee handbook");
        CanonicalRelation relation = new CanonicalRelation(
                RELATION_ID,
                FIRST_ENTITY_ID,
                SECOND_ENTITY_ID,
                RelationOrientation.DIRECTED);

        projection
                .add(entityContribution("first", first, FIRST_CHUNK_ID, ALLOWED_ASSET_ID), 0.95)
                .add(entityContribution("second", second, SECOND_CHUNK_ID, ALLOWED_ASSET_ID), 0.85)
                .add(relationContribution("relation", relation, RELATION_CHUNK_ID, ALLOWED_ASSET_ID), 0.90)
                .add(chunk(
                        FIRST_CHUNK_ID,
                        ORGANIZATION_ID,
                        ALLOWED_ASSET_ID,
                        "Probation lasts 60 days.",
                        "Employee handbook / Probation"),
                        0.0)
                .add(chunk(
                        SECOND_CHUNK_ID,
                        ORGANIZATION_ID,
                        ALLOWED_ASSET_ID,
                        "The handbook applies to full-time employees.",
                        "Employee handbook"),
                        0.0)
                .add(chunk(
                        RELATION_CHUNK_ID,
                        ORGANIZATION_ID,
                        ALLOWED_ASSET_ID,
                        "The handbook defines the probation policy.",
                        "Employee handbook"),
                        0.0)
                .add(chunk(
                        VECTOR_CHUNK_ID,
                        ORGANIZATION_ID,
                        ALLOWED_ASSET_ID,
                        "Employees are evaluated at the end of probation.",
                        "Employee handbook / Probation"),
                        0.96)
                .add(chunk(
                        RESTRICTED_CHUNK_ID,
                        ORGANIZATION_ID,
                        RESTRICTED_ASSET_ID,
                        "restricted finance forecast",
                        "Restricted"),
                        1.0)
                .add(chunk(
                        CROSS_TENANT_CHUNK_ID,
                        OTHER_ORGANIZATION_ID,
                        ALLOWED_ASSET_ID,
                        "cross tenant secret",
                        "Other tenant"),
                        1.0);
    }

    private LightRagQueryRequest request(
            LightRagQueryMode mode,
            QueryOutputMode outputMode,
            boolean rerankEnabled,
            boolean includeHeadings,
            boolean streaming,
            KeywordPlan trustedKeywords) {
        return new LightRagQueryRequest(
                scope(),
                snapshot(),
                "What is the probation policy?",
                new LightRagQueryRequest.Options(
                        mode,
                        outputMode,
                        "Concise",
                        "",
                        10,
                        10,
                        3,
                        1,
                        LightRagQueryRequest.RelatedChunkSelection.VECTOR,
                        new SecureContextBudget(500, 500, 2_000, 100),
                        rerankEnabled,
                        0.5,
                        0.2,
                        includeHeadings,
                        streaming),
                EMBEDDING_PROFILE_ID,
                3,
                trustedKeywords,
                List.of());
    }

    private static KeywordPlan trustedKeywords() {
        return new KeywordPlan(
                List.of("workplace policy"),
                List.of("probation"),
                KeywordPlan.Source.MODEL);
    }

    private static AuthorizedEvidenceScope scope() {
        return new AuthorizedEvidenceScope(
                ORGANIZATION_ID,
                ACTOR_ID,
                null,
                false,
                Set.of(ALLOWED_ASSET_ID),
                "model-v1",
                4,
                Instant.parse("2026-07-24T00:00:00Z"));
    }

    private static ProjectionSnapshot snapshot() {
        return new ProjectionSnapshot(
                id("batch"),
                new ProjectionNamespace(ORGANIZATION_ID, "default", "knowledge"),
                GENERATION,
                "manifest-v7",
                Set.of(ProjectionKind.CONTENT, ProjectionKind.VECTOR),
                Instant.parse("2026-07-24T00:00:00Z"));
    }

    private static EntityContribution entityContribution(
            String key,
            CanonicalEntity entity,
            UUID chunkId,
            UUID assetId) {
        return new EntityContribution(
                id(key + "-contribution"),
                entity,
                "POLICY",
                entity.normalizedName() + " description",
                provenance(key, chunkId, assetId));
    }

    private static RelationContribution relationContribution(
            String key,
            CanonicalRelation relation,
            UUID chunkId,
            UUID assetId) {
        return new RelationContribution(
                id(key + "-contribution"),
                relation,
                "DEFINED_BY",
                List.of("probation", "handbook"),
                "The employee handbook defines probation.",
                1.0,
                provenance(key, chunkId, assetId));
    }

    private static EvidenceProvenance provenance(
            String key,
            UUID chunkId,
            UUID assetId) {
        return new EvidenceProvenance(
                evidence(key, chunkId, ORGANIZATION_ID, assetId),
                GENERATION,
                "test",
                "test-model",
                "query-pr7",
                0.95,
                Instant.parse("2026-07-24T00:00:00Z"));
    }

    private static AuthorizedQueryProjection.Chunk chunk(
            UUID chunkId,
            UUID organizationId,
            UUID assetId,
            String content,
            String heading) {
        return new AuthorizedQueryProjection.Chunk(
                chunkId,
                evidence(chunkId.toString(), chunkId, organizationId, assetId),
                content,
                content.split("\\s+").length,
                Map.of(
                        "sourceLabel", "employee-handbook.md",
                        "heading", heading));
    }

    private static EvidenceReference evidence(
            String key,
            UUID chunkId,
            UUID organizationId,
            UUID assetId) {
        return new EvidenceReference(
                organizationId,
                assetId,
                id(key + "-revision"),
                chunkId,
                id(key + "-acl"),
                4);
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }

    private static List<String> iteratorValues(Iterator<String> iterator) {
        List<String> result = new ArrayList<>();
        iterator.forEachRemaining(result::add);
        return result;
    }

    private record ExpectedBranches(
            boolean entities,
            boolean relations,
            boolean vectorChunks) {
    }

    private static final class RecordingKeywordModel implements KeywordPlanningModel {

        private final KeywordPlan plan;
        private int calls;

        private RecordingKeywordModel(KeywordPlan plan) {
            this.plan = plan;
        }

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("keyword-test", "1");
        }

        @Override
        public KeywordPlan complete(String prompt) {
            calls++;
            return plan;
        }
    }

    private static final class RecordingEmbeddings implements TextEmbeddingPort {

        private final List<List<String>> batches = new ArrayList<>();

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("embedding-test", "1");
        }

        @Override
        public List<FloatVector> embedAll(List<String> texts) {
            batches.add(List.copyOf(texts));
            return texts.stream()
                    .map(ignored -> new FloatVector(new float[] {0.1f, 0.2f, 0.3f}))
                    .toList();
        }
    }

    private static final class WordTokenizer implements TextTokenizer {

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("tokenizer-test", "1");
        }

        @Override
        public EncodedText encode(String canonicalText) {
            if (canonicalText.isEmpty()) {
                return new EncodedText(canonicalText, new int[0], new int[0], new int[0]);
            }
            return new EncodedText(
                    canonicalText,
                    new int[] {canonicalText.hashCode()},
                    new int[] {0},
                    new int[] {canonicalText.length()});
        }

        @Override
        public int count(String canonicalText) {
            String normalized = canonicalText.strip();
            return normalized.isEmpty() ? 0 : normalized.split("\\s+").length;
        }
    }

    private static final class ConfigurableReranker implements ChunkReranker {

        private Map<UUID, Double> scores = Map.of();
        private RuntimeException failure;

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("reranker-test", "1");
        }

        @Override
        public List<Score> rerank(String query, List<Candidate> candidates, int limit) {
            if (failure != null) {
                throw failure;
            }
            return candidates.stream()
                    .map(candidate -> new Score(
                            candidate.chunkId(),
                            scores.getOrDefault(candidate.chunkId(), 1.0)))
                    .sorted((left, right) ->
                            Double.compare(right.relevance(), left.relevance()))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class RecordingAnswerModel implements QueryAnswerModel {

        private final List<Request> requests = new ArrayList<>();

        @Override
        public ProcessingComponentRef component() {
            return new ProcessingComponentRef("answer-test", "1");
        }

        @Override
        public Response answer(Request request) {
            requests.add(request);
            return request.streaming()
                    ? new Streaming(List.of("direct ", "answer").iterator())
                    : new Complete("grounded answer");
        }
    }
}
