package com.orgmemory.graphrag.extraction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.model.ExtractionDiagnostics;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.model.ExtractionRequest;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.model.RelationOrientation;
import com.orgmemory.graphrag.port.ExtractionModel;
import com.orgmemory.graphrag.testkit.CodePointTokenizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LightRagEntityRelationExtractorTests {

    @Test
    void gleanedRelationCanReferenceEntitiesFromTheInitialRound() {
        RecordingModel model = new RecordingModel(
                round(
                        "initial-json",
                        List.of(
                                entity("Alice", "PERSON", "An engineer."),
                                entity("Acme Corp", "ORGANIZATION", "An employer.")),
                        List.of()),
                round(
                        "glean-json",
                        List.of(),
                        List.of(relation(
                                "Alice",
                                "Acme Corp",
                                "WORKS_FOR",
                                "Alice works for Acme Corp."))));

        ExtractionResult result = extractor(model).extract(request(profile(1, 100_000)));

        assertEquals(2, result.entities().size());
        assertEquals(1, result.relations().size());
        assertEquals(
                ExtractionDiagnostics.GleaningOutcome.COMPLETED,
                result.diagnostics().gleaningOutcome());
        assertEquals(2, result.diagnostics().rounds().size());
        assertEquals(3, model.requests.get(1).conversation().size());
        assertEquals(
                ExtractionConversationMessage.Role.ASSISTANT,
                model.requests.get(1).conversation().get(1).role());
    }

    @Test
    void skipsGleaningBeforeTheProviderWhenTheFullConversationExceedsTheGuard() {
        RecordingModel model = new RecordingModel(round(
                "initial-json",
                List.of(entity("Alice", "PERSON", "An engineer.")),
                List.of()));

        ExtractionResult result = extractor(model).extract(request(profile(1, 1)));

        assertEquals(1, model.requests.size());
        assertEquals(
                ExtractionDiagnostics.GleaningOutcome.SKIPPED_TOKEN_LIMIT,
                result.diagnostics().gleaningOutcome());
    }

    @Test
    void configurableGuidanceLanguageAndTrustedExamplesAreRenderedByCore() {
        RecordingModel model = new RecordingModel(round(
                "initial-json",
                List.of(entity("Alice", "PERSON", "An engineer.")),
                List.of()));
        ExtractionProfile profile = new ExtractionProfile(
                "openai",
                "gpt-5.6-sol",
                LightRagExtractionPrompt.VERSION,
                10,
                12,
                List.of("PERSON", "BUSINESS_UNIT"),
                List.of("{\"entities\":[],\"relationships\":[]}"),
                0,
                0);

        extractor(model).extract(request(profile, "Engineering > Release"));

        ExtractionRoundRequest sent = model.requests.getFirst();
        assertTrue(sent.systemInstruction().contains("PERSON, BUSINESS_UNIT"));
        String user = sent.conversation().getFirst().content();
        assertTrue(user.contains("Required output language: vi-VN"));
        assertTrue(user.contains("Trusted examples:"));
        assertTrue(user.contains("---BEGIN UNTRUSTED SECTION CONTEXT---"));
        assertTrue(user.contains("Engineering > Release"));
        assertTrue(user.contains("---BEGIN UNTRUSTED EVIDENCE---"));
    }

    @Test
    void sectionContextIsTokenBoundedBeforeItReachesTheProvider() {
        RecordingModel model = new RecordingModel(round(
                "initial-json",
                List.of(entity("Alice", "PERSON", "An engineer.")),
                List.of()));
        ExtractionProfile profile = new ExtractionProfile(
                "openai",
                "gpt-5.6-sol",
                LightRagExtractionPrompt.VERSION,
                10,
                12,
                List.of("PERSON"),
                List.of(),
                0,
                0,
                4);

        extractor(model).extract(request(profile, "0123456789"));

        String prompt = model.requests.getFirst().conversation().getFirst().content();
        assertTrue(prompt.contains("\n0123\n"));
        assertFalse(prompt.contains("01234"));
    }

    @Test
    void gleaningKeepsTheLongerCorrectionForTheSameEntity() {
        RecordingModel model = new RecordingModel(
                round(
                        "initial-json",
                        List.of(entity("Alice", "PERSON", "Engineer.")),
                        List.of()),
                round(
                        "glean-json",
                        List.of(entity(
                                "Alice",
                                "PERSON",
                                "Alice is the engineer responsible for the release.")),
                        List.of()));

        ExtractionResult result = extractor(model).extract(request(profile(1, 100_000)));

        assertEquals(
                "Alice is the engineer responsible for the release.",
                result.entities().getFirst().description());
    }

    @Test
    void gleaningCorrectsOneEdgeWithoutSplittingItByRelationType() {
        RecordingModel model = new RecordingModel(
                round(
                        "initial-json",
                        List.of(
                                entity("Alice", "PERSON", "An engineer."),
                                entity("Acme Corp", "ORGANIZATION", "An employer.")),
                        List.of(relation(
                                "Alice",
                                "Acme Corp",
                                "RELATED_TO",
                                "Alice is related to Acme Corp."))),
                round(
                        "glean-json",
                        List.of(),
                        List.of(relation(
                                "Alice",
                                "Acme Corp",
                                "WORKS_FOR",
                                "Alice works for Acme Corp as a platform engineer."))));

        ExtractionResult result = extractor(model).extract(request(profile(1, 100_000)));

        assertEquals(1, result.relations().size());
        assertEquals("WORKS_FOR", result.relations().getFirst().type());
    }

    @Test
    void ignoresSelfRelationshipsLikeThePinnedLightRagOracle() {
        RecordingModel model = new RecordingModel(round(
                "initial-json",
                List.of(entity("Alice", "PERSON", "An engineer.")),
                List.of(relation(
                        "Alice",
                        "Alice",
                        "SAME_AS",
                        "Alice is the same person as Alice."))));

        ExtractionResult result = extractor(model).extract(request(profile(0, 0)));

        assertTrue(result.relations().isEmpty());
    }

    @Test
    void failsClosedWhenFinalRelationEndpointNeverResolves() {
        RecordingModel model = new RecordingModel(round(
                "initial-json",
                List.of(entity("Alice", "PERSON", "An engineer.")),
                List.of(relation(
                        "Alice",
                        "Missing Company",
                        "WORKS_FOR",
                        "Alice works for the missing company."))));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> extractor(model).extract(request(profile(0, 0))));

        assertTrue(exception.getMessage().contains("endpoint"));
    }

    private static LightRagEntityRelationExtractor extractor(ExtractionModel model) {
        return new LightRagEntityRelationExtractor(model, new CodePointTokenizer());
    }

    private static ExtractionProfile profile(int gleaningRounds, int gleaningInputTokens) {
        return new ExtractionProfile(
                "openai",
                "gpt-5.6-sol",
                LightRagExtractionPrompt.VERSION,
                10,
                12,
                List.of("PERSON", "ORGANIZATION"),
                List.of(),
                gleaningRounds,
                gleaningInputTokens);
    }

    private static ExtractionRequest request(ExtractionProfile profile) {
        return request(profile, null);
    }

    private static ExtractionRequest request(
            ExtractionProfile profile,
            String sectionContext) {
        return new ExtractionRequest(
                UUID.fromString("10000000-0000-0000-0000-000000000001"),
                UUID.fromString("20000000-0000-0000-0000-000000000001"),
                UUID.fromString("30000000-0000-0000-0000-000000000001"),
                UUID.fromString("40000000-0000-0000-0000-000000000001"),
                "Alice works for Acme Corp.",
                sectionContext,
                Locale.forLanguageTag("vi-VN"),
                profile);
    }

    private static ExtractionCandidateEntity entity(
            String name,
            String type,
            String description) {
        return new ExtractionCandidateEntity(name, type, description, 0.9);
    }

    private static ExtractionCandidateRelation relation(
            String source,
            String target,
            String type,
            String description) {
        return new ExtractionCandidateRelation(
                source,
                target,
                type,
                List.of("employment"),
                description,
                RelationOrientation.DIRECTED,
                1.0,
                0.9);
    }

    private static ExtractionRoundResponse round(
            String assistant,
            List<ExtractionCandidateEntity> entities,
            List<ExtractionCandidateRelation> relations) {
        return new ExtractionRoundResponse(assistant, entities, relations, 0, 0);
    }

    private static final class RecordingModel implements ExtractionModel {

        private final List<ExtractionRoundResponse> responses;
        private final List<ExtractionRoundRequest> requests = new ArrayList<>();

        private RecordingModel(ExtractionRoundResponse... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public ExtractionRoundResponse extract(ExtractionRoundRequest request) {
            requests.add(request);
            return responses.get(requests.size() - 1);
        }
    }
}
