package com.orgmemory.graphrag.export;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.graphrag.model.EvidenceReference;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphExportFormatterTests {

    private final GraphExportFormatter formatter = new GraphExportFormatter();

    @Test
    void jsonIsDeterministicAndCarriesEvidenceProvenance() {
        GraphExportDocument first = document(false);
        GraphExportDocument reversed = document(true);

        GraphExportFormatter.Artifact baseline =
                formatter.format(first, GraphExportFormat.JSON);
        GraphExportFormatter.Artifact replay =
                formatter.format(reversed, GraphExportFormat.JSON);

        assertEquals(baseline, replay);
        assertEquals("application/json", baseline.mediaType());
        assertTrue(baseline.content().contains("\"aclGeneration\":7"));
        assertTrue(baseline.content().contains("\"knowledgeAssetId\""));
    }

    @Test
    void csvQuotesUntrustedTextAndDoesNotInventDeniedRows() {
        GraphExportFormatter.Artifact artifact =
                formatter.format(document(false), GraphExportFormat.CSV);

        assertTrue(artifact.content().contains("\"Policy, internal\""));
        assertTrue(artifact.content().contains("\"A \"\"quoted\"\" description\""));
        assertFalse(artifact.content().contains("denied"));
    }

    private static GraphExportDocument document(boolean reversed) {
        var first = new GraphExportDocument.EntityRow(
                uuid("entity-a"),
                "Policy, internal",
                "DOCUMENT",
                "A \"quoted\" description",
                List.of(evidence("a")));
        var second = new GraphExportDocument.EntityRow(
                uuid("entity-b"),
                "Employee",
                "PERSON",
                "An employee",
                List.of(evidence("b")));
        var relation = new GraphExportDocument.RelationRow(
                uuid("relation"),
                first.id(),
                second.id(),
                "APPLIES_TO",
                List.of("policy"),
                "Policy applies to employee",
                1.0,
                List.of(evidence("relation")));
        return new GraphExportDocument(
                reversed ? List.of(second, first) : List.of(first, second),
                List.of(relation));
    }

    private static EvidenceReference evidence(String value) {
        return new EvidenceReference(
                uuid("organization"),
                uuid("asset-" + value),
                uuid("revision-" + value),
                uuid("chunk-" + value),
                uuid("acl-" + value),
                7);
    }

    private static UUID uuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8));
    }
}
