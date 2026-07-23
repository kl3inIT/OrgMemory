package com.orgmemory.integrations.graphrag.sidecar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.multimodal.MultimodalBinaryArtifact;
import com.orgmemory.graphrag.multimodal.MultimodalPayload;
import com.orgmemory.graphrag.multimodal.MultimodalSidecarItem;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LightRagSidecarDecoderTests {

    private static final String CONTENT = """
            # Leave policy
            Diagram <drawing id="im-doc-0001" format="png" path="policy.blocks.assets/leave.png" />

            Table <table id="tb-doc-0001" format="json">[["Years","Days"],[1,12]]</table>

            Equation <equation id="eq-doc-0001" format="latex">d=12+y</equation>
            """.strip();

    @Test
    void decodesAllModalitiesAndRecomputesCanonicalLineage() {
        AtomicReference<String> resolvedPath = new AtomicReference<>();
        LightRagSidecarDecoder decoder = new LightRagSidecarDecoder(
                new ObjectMapper(),
                (itemId, path, mediaType) -> {
                    resolvedPath.set(path);
                    return artifact(itemId, mediaType);
                });

        DocumentParseResult result = decoder.decode(bundle(
                "policy.blocks.assets/leave.png",
                documentHash()));

        assertEquals(documentHash(), result.document().contentSha256());
        assertTrue(result.multimodalSidecar().isPresent());
        assertEquals(
                3,
                result.multimodalSidecar().orElseThrow().items().size());
        assertEquals("policy.blocks.assets/leave.png", resolvedPath.get());
        MultimodalSidecarItem image =
                result.multimodalSidecar().orElseThrow().items().getFirst();
        assertInstanceOf(MultimodalPayload.Image.class, image.payload());
        assertEquals(
                "Diagram <drawing id=\"im-doc-0001\" format=\"png\" "
                        + "path=\"policy.blocks.assets/leave.png\" />",
                result.document()
                        .content()
                        .substring(image.targetStartChar() - 8, image.targetEndChar()));
        assertFalse(image.attributes().containsKey("llm_analyze_result"));
    }

    @Test
    void rejectsTraversalBeforeCallingTheArtifactResolver() {
        AtomicReference<String> resolvedPath = new AtomicReference<>();
        LightRagSidecarDecoder decoder = new LightRagSidecarDecoder(
                new ObjectMapper(),
                (itemId, path, mediaType) -> {
                    resolvedPath.set(path);
                    return artifact(itemId, mediaType);
                });

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> decoder.decode(bundle("../secret.png", documentHash())));

        assertTrue(exception.getMessage().contains("unsafe"));
        assertEquals(null, resolvedPath.get());
    }

    @Test
    void rejectsABundleWhoseDeclaredHashDoesNotMatchTheBody() {
        LightRagSidecarDecoder decoder = new LightRagSidecarDecoder(
                new ObjectMapper(),
                (itemId, path, mediaType) -> artifact(itemId, mediaType));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> decoder.decode(bundle(
                        "policy.blocks.assets/leave.png",
                        "f".repeat(64))));

        assertTrue(exception.getMessage().contains("hash"));
    }

    @Test
    void rejectsDuplicateBlockIdsInsteadOfSilentlyReplacingTheAnchor() {
        LightRagSidecarDecoder decoder = new LightRagSidecarDecoder(
                new ObjectMapper(),
                (itemId, path, mediaType) -> artifact(itemId, mediaType));
        String blocks = """
                {"type":"meta","format":"lightrag","version":"1.0","document_name":"leave-policy.docx","document_format":"docx","document_hash":"sha256:%s","blocks":2,"doc_id":"doc-1","parse_engine":"native"}
                {"type":"content","blockid":"block-1","format":"plain_text","content":"First","heading":"","parent_headings":[],"level":0,"session_type":"body"}
                {"type":"content","blockid":"block-1","format":"plain_text","content":"Second","heading":"","parent_headings":[],"level":0,"session_type":"body"}
                """.formatted(ResolvedDocumentProcessingProfile.sha256("First\n\nSecond"));

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> decoder.decode(new LightRagSidecarBundle(
                        blocks,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty())));

        assertTrue(exception.getMessage().contains("duplicate blockid"));
    }

    private static LightRagSidecarBundle bundle(
            String imagePath,
            String hash) {
        String blocks = """
                {"type":"meta","format":"lightrag","version":"1.0","document_name":"leave-policy.docx","document_format":"docx","document_hash":"sha256:%s","blocks":1,"doc_id":"doc-1","parse_engine":"native"}
                {"type":"content","blockid":"block-1","format":"plain_text","content":%s,"heading":"Leave policy","parent_headings":[],"level":1,"session_type":"body"}
                """.formatted(hash, jsonString(CONTENT));
        String drawings = """
                {
                  "version": "1.0",
                  "drawings": {
                    "im-doc-0001": {
                      "id": "im-doc-0001",
                      "blockid": "block-1",
                      "heading": "Leave policy",
                      "parent_headings": [],
                      "format": "png",
                      "path": "%s",
                      "caption": "Leave entitlement flow",
                      "footnotes": [],
                      "llm_analyze_result": {
                        "status": "success",
                        "description": "untrusted stale result"
                      }
                    }
                  }
                }
                """.formatted(imagePath);
        String tables = """
                {
                  "version": "1.0",
                  "tables": {
                    "tb-doc-0001": {
                      "id": "tb-doc-0001",
                      "blockid": "block-1",
                      "heading": "Leave policy",
                      "parent_headings": [],
                      "dimension": [2, 2],
                      "format": "json",
                      "content": "[[\\"Years\\",\\"Days\\"],[1,12]]",
                      "caption": "",
                      "footnotes": []
                    }
                  }
                }
                """;
        String equations = """
                {
                  "version": "1.0",
                  "equations": {
                    "eq-doc-0001": {
                      "id": "eq-doc-0001",
                      "blockid": "block-1",
                      "heading": "Leave policy",
                      "parent_headings": [],
                      "format": "latex",
                      "content": "d=12+y",
                      "caption": "",
                      "footnotes": []
                    }
                  }
                }
                """;
        return new LightRagSidecarBundle(
                blocks,
                Optional.of(drawings),
                Optional.of(tables),
                Optional.of(equations));
    }

    private static MultimodalBinaryArtifact artifact(
            String itemId,
            String mediaType) {
        return new MultimodalBinaryArtifact(
                "artifact:" + itemId,
                mediaType,
                128,
                "a".repeat(64),
                OptionalInt.of(20),
                OptionalInt.of(10));
    }

    private static String documentHash() {
        return ResolvedDocumentProcessingProfile.sha256(CONTENT);
    }

    private static String jsonString(String value) {
        return new ObjectMapper().writeValueAsString(value);
    }
}
