package com.orgmemory.graphrag.multimodal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.testkit.CodePointTokenizer;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MultimodalProcessorTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID REVISION_ID =
            UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final UUID ACL_SNAPSHOT_ID =
            UUID.fromString("30000000-0000-0000-0000-000000000001");

    @Test
    void materializesAllSupportedModalitiesWithInheritedAclAndProvenance() {
        CanonicalDocument document = CanonicalDocument.text(
                "Before <drawing id=\"image-1\" /> between "
                        + "<table id=\"table-1\">[]</table> and "
                        + "<equation id=\"equation-1\">x=1</equation> after.");
        MultimodalSidecar sidecar = sidecar(document);
        MultimodalProcessor processor = processor(
                successfulAnalyzer(MultimodalAnalyzerRole.VISION),
                successfulAnalyzer(MultimodalAnalyzerRole.TEXT_EXTRACTION));

        MultimodalProcessingResult result = processor.process(
                document,
                sidecar,
                scope(),
                document.contentSha256(),
                "native@1",
                profile(Set.of()));

        assertTrue(result.publishable());
        assertEquals(3, result.outcomes().size());
        assertEquals(3, result.chunks().size());
        assertTrue(result.chunks().stream()
                .allMatch(chunk -> chunk.evidenceScope().equals(scope())));
        assertTrue(result.chunks().stream()
                .allMatch(chunk -> chunk.analysisCacheKey().value().length() == 64));
        assertTrue(result.chunks().stream()
                .map(MultimodalDerivedChunk::content)
                .anyMatch(content -> content.contains("[Image Type] Diagram")));
        assertTrue(result.chunks().stream()
                .map(MultimodalDerivedChunk::content)
                .anyMatch(content -> content.contains("[Table Name] Leave policy")));
        assertTrue(result.chunks().stream()
                .map(MultimodalDerivedChunk::content)
                .anyMatch(content -> content.contains("[Equation Name] Accrual")));
    }

    @Test
    void requiredDeterministicSkipBlocksPublicationWithoutCallingTheModel() {
        CanonicalDocument document = CanonicalDocument.text(
                "Before <drawing id=\"image-1\" /> after.");
        MultimodalSidecar sidecar = new MultimodalSidecar(
                MultimodalSidecar.SCHEMA_VERSION,
                "doc-1",
                "policy.txt",
                "txt",
                "native",
                document.contentSha256(),
                List.of(imageItem(document)));
        var analyzer = new CountingAnalyzer(MultimodalAnalyzerRole.VISION);

        MultimodalProcessingResult result = processor(analyzer).process(
                document,
                sidecar,
                scope(),
                document.contentSha256(),
                "native@1",
                profile(Set.of(MultimodalModality.IMAGE), 4));

        assertFalse(result.publishable());
        assertEquals(0, analyzer.calls);
        MultimodalAnalysisOutcome.Skipped skipped = assertInstanceOf(
                MultimodalAnalysisOutcome.Skipped.class,
                result.outcomes().getFirst());
        assertEquals("IMAGE_TOO_LARGE", skipped.reasonCode());
    }

    @Test
    void analyzerFailureRemainsAFailureAndBlocksPublication() {
        CanonicalDocument document = CanonicalDocument.text(
                "Before <drawing id=\"image-1\" /> after.");
        MultimodalSidecar sidecar = new MultimodalSidecar(
                MultimodalSidecar.SCHEMA_VERSION,
                "doc-1",
                "policy.txt",
                "txt",
                "native",
                document.contentSha256(),
                List.of(imageItem(document)));
        MultimodalAnalyzer failing = new MultimodalAnalyzer() {
            @Override
            public MultimodalAnalyzerRole role() {
                return MultimodalAnalyzerRole.VISION;
            }

            @Override
            public MultimodalAnalysisOutcome analyze(
                    MultimodalAnalysisRequest request) {
                return new MultimodalAnalysisOutcome.Failure(
                        request.item().itemId(),
                        request.item().modality(),
                        "PROVIDER_TIMEOUT",
                        "Provider timed out",
                        true);
            }
        };

        MultimodalProcessingResult result = processor(failing).process(
                document,
                sidecar,
                scope(),
                document.contentSha256(),
                "native@1",
                profile(Set.of()));

        assertFalse(result.publishable());
        assertTrue(result.chunks().isEmpty());
        assertInstanceOf(
                MultimodalAnalysisOutcome.Failure.class,
                result.outcomes().getFirst());
    }

    private static MultimodalProcessor processor(MultimodalAnalyzer... analyzers) {
        var tokenizer = new CodePointTokenizer();
        return new MultimodalProcessor(
                new MultimodalContextBuilder(tokenizer),
                new MultimodalChunkAssembler(tokenizer),
                List.of(analyzers));
    }

    private static MultimodalAnalyzer successfulAnalyzer(
            MultimodalAnalyzerRole role) {
        return new MultimodalAnalyzer() {
            @Override
            public MultimodalAnalyzerRole role() {
                return role;
            }

            @Override
            public MultimodalAnalysisOutcome analyze(
                    MultimodalAnalysisRequest request) {
                MultimodalAnalysisContent content = switch (request.item().modality()) {
                    case IMAGE -> new MultimodalAnalysisContent.Image(
                            "Onboarding", "Diagram", "Employee onboarding flow.");
                    case TABLE -> new MultimodalAnalysisContent.Table(
                            "Leave policy", "Annual leave entitlement.");
                    case EQUATION -> new MultimodalAnalysisContent.Equation(
                            "Accrual", "x=1", "Leave accrual expression.");
                };
                return new MultimodalAnalysisOutcome.Success(
                        request.item().itemId(),
                        request.item().modality(),
                        content,
                        request.evidenceScope(),
                        request.surroundingContext(),
                        request.route(),
                        request.cacheKey());
            }
        };
    }

    private static MultimodalSidecar sidecar(CanonicalDocument document) {
        return new MultimodalSidecar(
                MultimodalSidecar.SCHEMA_VERSION,
                "doc-1",
                "policy.txt",
                "txt",
                "native",
                document.contentSha256(),
                List.of(
                        imageItem(document),
                        item(
                                document,
                                "table-1",
                                MultimodalModality.TABLE,
                                new MultimodalPayload.Table("json", "[]", 1, 1)),
                        item(
                                document,
                                "equation-1",
                                MultimodalModality.EQUATION,
                                new MultimodalPayload.Equation("x=1"))));
    }

    private static MultimodalSidecarItem imageItem(CanonicalDocument document) {
        return item(
                document,
                "image-1",
                MultimodalModality.IMAGE,
                new MultimodalPayload.Image(new MultimodalBinaryArtifact(
                        "artifact-1",
                        "image/png",
                        8,
                        "a".repeat(64),
                        OptionalInt.of(2),
                        OptionalInt.of(2))));
    }

    private static MultimodalSidecarItem item(
            CanonicalDocument document,
            String itemId,
            MultimodalModality modality,
            MultimodalPayload payload) {
        int id = document.content().indexOf(itemId);
        int start = document.content().lastIndexOf('<', id);
        int end = document.content().indexOf('>', id) + 1;
        if (modality != MultimodalModality.IMAGE) {
            String closing = modality == MultimodalModality.TABLE
                    ? "</table>"
                    : "</equation>";
            end = document.content().indexOf(closing, id) + closing.length();
        }
        return new MultimodalSidecarItem(
                itemId,
                modality,
                0,
                start,
                end,
                List.of("People policy"),
                "",
                "",
                payload,
                Map.of());
    }

    private static MultimodalEvidenceScope scope() {
        return new MultimodalEvidenceScope(
                ORGANIZATION_ID, REVISION_ID, ACL_SNAPSHOT_ID, 7);
    }

    private static MultimodalProcessingProfile profile(
            Set<MultimodalModality> required) {
        return profile(required, 1024);
    }

    private static MultimodalProcessingProfile profile(
            Set<MultimodalModality> required,
            long maximumImageBytes) {
        var routes = new EnumMap<MultimodalModality, MultimodalAnalysisRoute>(
                MultimodalModality.class);
        routes.put(
                MultimodalModality.IMAGE,
                route(MultimodalAnalyzerRole.VISION));
        routes.put(
                MultimodalModality.TABLE,
                route(MultimodalAnalyzerRole.TEXT_EXTRACTION));
        routes.put(
                MultimodalModality.EQUATION,
                route(MultimodalAnalyzerRole.TEXT_EXTRACTION));
        return MultimodalProcessingProfile.resolve(
                EnumSet.allOf(MultimodalModality.class),
                required,
                routes,
                30,
                30,
                maximumImageBytes,
                1_000_000,
                200);
    }

    private static MultimodalAnalysisRoute route(MultimodalAnalyzerRole role) {
        return new MultimodalAnalysisRoute(
                "openai",
                "gpt-5.6-sol",
                "2026-07-01",
                MultimodalPromptFactory.VERSION,
                role,
                0.0,
                500);
    }

    private static final class CountingAnalyzer implements MultimodalAnalyzer {

        private final MultimodalAnalyzerRole role;
        private int calls;

        private CountingAnalyzer(MultimodalAnalyzerRole role) {
            this.role = role;
        }

        @Override
        public MultimodalAnalyzerRole role() {
            return role;
        }

        @Override
        public MultimodalAnalysisOutcome analyze(
                MultimodalAnalysisRequest request) {
            calls++;
            throw new AssertionError("model must not be called");
        }
    }
}
