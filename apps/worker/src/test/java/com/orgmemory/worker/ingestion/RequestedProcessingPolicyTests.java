package com.orgmemory.worker.ingestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class RequestedProcessingPolicyTests {

    @Test
    void defaultsToTheVersionedStructuredBlockPolicy() {
        DocumentProcessingEngine engine = engine(properties(null, 800));

        var snapshot = engine.requestedProcessingProfile();
        RequestedProcessingPolicy restored = engine.requestedPolicy(snapshot);

        assertEquals(RequestedProcessingPolicy.STRUCTURED_BLOCK_V1, restored.policyId());
        assertEquals("paragraph-semantic", restored.requestedChunkerId());
        assertTrue(snapshot.canonicalForm().contains("dispatch.table=paragraph-semantic%3Atable-row-v1"));
        assertEquals(snapshot, restored.snapshot());
    }

    @Test
    void changesIdentityWhenAnOutputAffectingOptionChanges() {
        var smaller = engine(properties(null, 400)).requestedProcessingProfile();
        var larger = engine(properties(null, 800)).requestedProcessingProfile();

        assertNotEquals(smaller.sha256(), larger.sha256());
    }

    @Test
    void preservesNamedOperatorPoliciesWithoutReadingARawChunkerId() {
        DocumentProcessingEngine engine = engine(properties(
                RequestedProcessingPolicy.SEMANTIC_VECTOR_V1, 800));
        RequestedProcessingPolicy restored =
                engine.requestedPolicy(engine.requestedProcessingProfile());

        assertEquals("semantic-vector", restored.requestedChunkerId());
    }

    @Test
    void refusesARetryWhenAPinnedComponentVersionIsUnavailable() {
        DocumentProcessingEngine engine = engine(properties(null, 800));
        DocumentProcessingProfileSnapshot available = engine.requestedProcessingProfile();
        DocumentProcessingProfileSnapshot unavailable = DocumentProcessingProfileSnapshot.from(
                available.canonicalForm().replace(
                        "parser.version=2.1.0", "parser.version=removed-version"));

        assertThrows(IllegalStateException.class, () -> engine.requestedPolicy(unavailable));
    }

    @Test
    void refusesAProfileWhoseCanonicalKeysWereReordered() {
        DocumentProcessingEngine engine = engine(properties(null, 800));
        DocumentProcessingProfileSnapshot canonical = engine.requestedProcessingProfile();
        String reordered = canonical.canonicalForm().lines()
                .sorted(java.util.Comparator.reverseOrder())
                .reduce("", (left, right) -> left + right + "\n");

        assertThrows(
                IllegalArgumentException.class,
                () -> engine.requestedPolicy(DocumentProcessingProfileSnapshot.from(reordered)));
    }

    private static DocumentProcessingEngine engine(SourceProcessingProperties properties) {
        return new DocumentProcessingEngine(properties, new SpringAiDocumentParser());
    }

    private static SourceProcessingProperties properties(String policyId, int chunkSize) {
        return new SourceProcessingProperties(
                "policy-test-worker",
                Duration.ofMinutes(1),
                "test-pipeline",
                "spring-ai-document-reader",
                policyId,
                "o200k_base",
                "normalizer",
                "fixture",
                "fixture-model",
                64,
                chunkSize,
                0,
                16,
                100,
                1,
                Duration.ofSeconds(30));
    }
}
