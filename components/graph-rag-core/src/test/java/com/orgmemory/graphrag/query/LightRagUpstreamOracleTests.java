package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.graphrag.chunking.ChunkingRequest;
import com.orgmemory.graphrag.chunking.FixedTokenChunker;
import com.orgmemory.graphrag.chunking.FixedTokenOptions;
import com.orgmemory.graphrag.indexing.LightRagEmbeddingPayloads;
import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.testkit.CodePointTokenizer;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class LightRagUpstreamOracleTests {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void javaSemanticsMatchTheExecutablePinnedUpstreamOracle() throws IOException {
        JsonNode oracle;
        try (var stream = Objects.requireNonNull(
                LightRagUpstreamOracleTests.class.getResourceAsStream(
                        "/lightrag-v1.5.4-oracle.json"))) {
            oracle = mapper.readTree(stream);
        }
        assertEquals(
                "9a45b64c2ee25b1d806e90db926a8af37480bb16",
                oracle.at("/upstream/commit").asString());

        JsonNode chunking = oracle.at("/fixture/chunking");
        var chunks = new FixedTokenChunker().chunk(
                new ChunkingRequest(
                        CanonicalDocument.text(chunking.get("content").asString()),
                        new CodePointTokenizer(),
                        Optional.empty()),
                new FixedTokenOptions(
                        chunking.get("chunkTokenSize").asInt(),
                        chunking.get("chunkOverlapTokenSize").asInt(),
                        null,
                        false));
        JsonNode expectedChunks = oracle.at("/expected/chunks");
        assertEquals(
                values(expectedChunks, "content"),
                chunks.stream().map(chunk -> chunk.content()).toList());
        assertEquals(
                integers(expectedChunks, "tokens"),
                chunks.stream().map(chunk -> chunk.tokenCount()).toList());
        assertEquals(
                integers(expectedChunks, "_source_span", "start"),
                chunks.stream()
                        .map(chunk -> chunk.provenance().startChar())
                        .toList());
        assertEquals(
                integers(expectedChunks, "_source_span", "end"),
                chunks.stream()
                        .map(chunk -> chunk.provenance().endChar())
                        .toList());

        JsonNode polling = oracle.at("/fixture/weightedPolling");
        List<List<UUID>> groups = polling.get("groups").valueStream()
                .map(group -> group.valueStream()
                        .map(JsonNode::asString)
                        .map(LightRagUpstreamOracleTests::id)
                        .toList())
                .toList();
        assertEquals(
                oracle.at("/expected/weightedPolling").valueStream()
                        .map(JsonNode::asString)
                        .map(LightRagUpstreamOracleTests::id)
                        .toList(),
                LightRagQueryEngine.weightedPolling(
                        groups,
                        polling.get("maximumRelatedChunks").asInt(),
                        polling.get("minimumRelatedChunks").asInt()));

        JsonNode payloads = oracle.at("/fixture/embeddingPayloads");
        assertEquals(
                oracle.at("/expected/entityEmbeddingPayload").asString(),
                LightRagEmbeddingPayloads.entity(
                        payloads.get("entityName").asString(),
                        payloads.get("entityDescription").asString()));
        assertEquals(
                oracle.at("/expected/relationEmbeddingPayload").asString(),
                LightRagEmbeddingPayloads.relation(
                        List.of("governs", "leave"),
                        payloads.get("relationSource").asString(),
                        payloads.get("relationTarget").asString(),
                        payloads.get("relationDescription").asString()));
    }

    private static List<String> values(JsonNode array, String field) {
        return array.valueStream()
                .map(node -> node.get(field).asString())
                .toList();
    }

    private static List<Integer> integers(
            JsonNode array, String objectField, String valueField) {
        return array.valueStream()
                .map(node -> node.get(objectField).get(valueField).asInt())
                .toList();
    }

    private static List<Integer> integers(JsonNode array, String field) {
        return array.valueStream()
                .map(node -> node.get(field).asInt())
                .toList();
    }

    private static UUID id(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
