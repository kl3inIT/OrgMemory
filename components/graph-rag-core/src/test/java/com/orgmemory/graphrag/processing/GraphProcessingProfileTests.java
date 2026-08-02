package com.orgmemory.graphrag.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.extraction.LightRagExtractionPrompt;
import com.orgmemory.graphrag.indexing.GraphContributionAssembler;
import com.orgmemory.graphrag.indexing.LightRagEmbeddingPayloads;
import com.orgmemory.graphrag.model.ExtractionProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphProcessingProfileTests {

    private static final String SCHEMA_V1_CANONICAL = """
            schemaVersion=1
            algorithmVersion=bGlnaHRyYWctdjEuNS40LXRlc3Q
            extraction.provider=b3BlbmFp
            extraction.model=Z3B0LXRlc3Q
            extraction.promptVersion=cHJvbXB0LXYx
            extraction.maxEntities=4
            extraction.maxRelations=6
            extraction.entityType.count=2
            extraction.entityType.0=UEVSU09O
            extraction.entityType.1=UE9MSUNZ
            extraction.example.count=1
            extraction.example.0=RXhhbXBsZQp3aXRoIG5ld2xpbmU
            extraction.maxGleaningRounds=1
            extraction.maxGleaningInputTokens=2048
            extraction.maxSectionContextTokens=256
            promptTemplate=U3lzdGVtICVzClVzZXIgJXM
            mergeSemanticsVersion=bWVyZ2UtdjE
            embeddingPayloadFormatVersion=cGF5bG9hZC12MQ
            """;
    private static final String SCHEMA_V1_SHA =
            "58f2c737474f3dfe4ff00859cd54cf81ff0316eca15be09067b208b8b6cd54d0";

    @Test
    void canonicalSnapshotRoundTripsWithoutLosingPromptOrExamples() {
        GraphProcessingProfile profile = profile();

        GraphProcessingProfile restored = GraphProcessingProfile.restore(
                profile.canonicalForm(), profile.canonicalSha256());

        assertEquals(profile, restored);
        assertEquals("Example\nwith newline", restored.extractionProfile().examples().getFirst());
        assertEquals(2, restored.schemaVersion());
    }

    @Test
    void restoresTheExactPersistedSchemaV1BytesAsProviderDefault() {
        GraphProcessingProfile restored = GraphProcessingProfile.restore(
                SCHEMA_V1_CANONICAL,
                SCHEMA_V1_SHA);

        assertEquals(1, restored.schemaVersion());
        assertEquals(SCHEMA_V1_CANONICAL, restored.canonicalForm());
        assertEquals(SCHEMA_V1_SHA, restored.canonicalSha256());
        assertNull(restored.extractionProfile().openAiReasoningEffort());
        assertEquals("gpt-test", restored.extractionProfile().model());
    }

    @Test
    void schemaV1RejectsReasoningThatItsCanonicalIdentityCannotRepresent() {
        GraphProcessingProfile restored = GraphProcessingProfile.restore(
                SCHEMA_V1_CANONICAL,
                SCHEMA_V1_SHA);
        ExtractionProfile extraction = restored.extractionProfile();

        assertThrows(
                IllegalArgumentException.class,
                () -> new GraphProcessingProfile(
                        1,
                        restored.algorithmVersion(),
                        new ExtractionProfile(
                                extraction.provider(),
                                extraction.model(),
                                "none",
                                extraction.promptVersion(),
                                extraction.maxEntities(),
                                extraction.maxRelations(),
                                extraction.entityTypeGuidance(),
                                extraction.examples(),
                                extraction.maxGleaningRounds(),
                                extraction.maxGleaningInputTokens(),
                                extraction.maxSectionContextTokens()),
                        restored.promptTemplate(),
                        restored.mergeSemanticsVersion(),
                        restored.embeddingPayloadFormatVersion(),
                        restored.canonicalSha256()));
    }

    @Test
    void currentWorkerSemanticsContinueToSupportQueuedSchemaV1Profiles() {
        GraphProcessingProfile current =
                LightRagGraphProcessingProfiles.current(profile().extractionProfile());
        assertEquals(
                LightRagGraphProcessingProfiles.ALGORITHM_VERSION,
                current.algorithmVersion());
        assertEquals(
                LightRagExtractionPrompt.templateSnapshot().strip(),
                current.promptTemplate());
        assertEquals(
                GraphContributionAssembler.MERGE_SEMANTICS_VERSION,
                current.mergeSemanticsVersion());
        assertEquals(
                LightRagEmbeddingPayloads.FORMAT_ID,
                current.embeddingPayloadFormatVersion());
        assertTrue(LightRagGraphProcessingProfiles.supports(current));
        String schemaV1Canonical = current.canonicalForm()
                .replaceFirst("schemaVersion=2", "schemaVersion=1")
                .replaceFirst("extraction\\.openAiReasoningEffort=\\R", "");
        GraphProcessingProfile queuedBeforeUpgrade = GraphProcessingProfile.restore(
                schemaV1Canonical,
                ResolvedDocumentProcessingProfile.sha256(schemaV1Canonical));

        assertEquals(1, queuedBeforeUpgrade.schemaVersion());
        assertNull(queuedBeforeUpgrade.extractionProfile().openAiReasoningEffort());
        assertEquals(current.algorithmVersion(), queuedBeforeUpgrade.algorithmVersion());
        assertEquals(current.promptTemplate(), queuedBeforeUpgrade.promptTemplate());
        assertEquals(current.mergeSemanticsVersion(), queuedBeforeUpgrade.mergeSemanticsVersion());
        assertEquals(
                current.embeddingPayloadFormatVersion(),
                queuedBeforeUpgrade.embeddingPayloadFormatVersion());
        assertTrue(LightRagGraphProcessingProfiles.supports(queuedBeforeUpgrade));
    }

    @Test
    void openAiReasoningEffortChangesOnlyNewSchemaV2Identity() {
        GraphProcessingProfile omitted = profile();
        ExtractionProfile extraction = omitted.extractionProfile();
        GraphProcessingProfile disabled = GraphProcessingProfile.resolve(
                omitted.algorithmVersion(),
                new ExtractionProfile(
                        extraction.provider(),
                        extraction.model(),
                        "none",
                        extraction.promptVersion(),
                        extraction.maxEntities(),
                        extraction.maxRelations(),
                        extraction.entityTypeGuidance(),
                        extraction.examples(),
                        extraction.maxGleaningRounds(),
                        extraction.maxGleaningInputTokens(),
                        extraction.maxSectionContextTokens()),
                omitted.promptTemplate(),
                omitted.mergeSemanticsVersion(),
                omitted.embeddingPayloadFormatVersion());

        assertEquals(2, disabled.schemaVersion());
        assertNotEquals(omitted.canonicalSha256(), disabled.canonicalSha256());
    }

    @Test
    void everyIndependentAlgorithmCoordinateChangesTheCanonicalHash() {
        GraphProcessingProfile original = profile();
        ExtractionProfile extraction = original.extractionProfile();

        assertNotEquals(
                original.canonicalSha256(),
                GraphProcessingProfile.resolve(
                                original.algorithmVersion() + "-next",
                                extraction,
                                original.promptTemplate(),
                                original.mergeSemanticsVersion(),
                                original.embeddingPayloadFormatVersion())
                        .canonicalSha256());
        assertNotEquals(
                original.canonicalSha256(),
                GraphProcessingProfile.resolve(
                                original.algorithmVersion(),
                                new ExtractionProfile(
                                        extraction.provider(),
                                        extraction.model() + "-next",
                                        extraction.promptVersion(),
                                        extraction.maxEntities(),
                                        extraction.maxRelations(),
                                        extraction.entityTypeGuidance(),
                                        extraction.examples(),
                                        extraction.maxGleaningRounds(),
                                        extraction.maxGleaningInputTokens(),
                                        extraction.maxSectionContextTokens()),
                                original.promptTemplate(),
                                original.mergeSemanticsVersion(),
                                original.embeddingPayloadFormatVersion())
                        .canonicalSha256());
        assertNotEquals(
                original.canonicalSha256(),
                GraphProcessingProfile.resolve(
                                original.algorithmVersion(),
                                extraction,
                                original.promptTemplate() + "\nchanged",
                                original.mergeSemanticsVersion(),
                                original.embeddingPayloadFormatVersion())
                        .canonicalSha256());
        assertNotEquals(
                original.canonicalSha256(),
                GraphProcessingProfile.resolve(
                                original.algorithmVersion(),
                                extraction,
                                original.promptTemplate(),
                                original.mergeSemanticsVersion() + "-next",
                                original.embeddingPayloadFormatVersion())
                        .canonicalSha256());
        assertNotEquals(
                original.canonicalSha256(),
                GraphProcessingProfile.resolve(
                                original.algorithmVersion(),
                                extraction,
                                original.promptTemplate(),
                                original.mergeSemanticsVersion(),
                                original.embeddingPayloadFormatVersion() + "-next")
                        .canonicalSha256());
    }

    @Test
    void restoreRejectsCanonicalTextThatDoesNotMatchTheStoredHash() {
        GraphProcessingProfile profile = profile();

        assertThrows(
                IllegalArgumentException.class,
                () -> GraphProcessingProfile.restore(
                        profile.canonicalForm().replace("schemaVersion=2", "schemaVersion=3"),
                        profile.canonicalSha256()));
    }

    private static GraphProcessingProfile profile() {
        return GraphProcessingProfile.resolve(
                "lightrag-v1.5.4-test",
                new ExtractionProfile(
                        "openai",
                        "gpt-test",
                        "prompt-v1",
                        4,
                        6,
                        List.of("PERSON", "POLICY"),
                        List.of("Example\nwith newline"),
                        1,
                        2048,
                        256),
                "System %s\nUser %s",
                "merge-v1",
                "payload-v1");
    }
}
