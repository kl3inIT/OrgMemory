package com.orgmemory.graphrag.processing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.model.ExtractionProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphProcessingProfileTests {

    @Test
    void canonicalSnapshotRoundTripsWithoutLosingPromptOrExamples() {
        GraphProcessingProfile profile = profile();

        GraphProcessingProfile restored = GraphProcessingProfile.restore(
                profile.canonicalForm(), profile.canonicalSha256());

        assertEquals(profile, restored);
        assertEquals("Example\nwith newline", restored.extractionProfile().examples().getFirst());
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
                        profile.canonicalForm().replace("schemaVersion=1", "schemaVersion=2"),
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
