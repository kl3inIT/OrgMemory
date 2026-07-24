package com.orgmemory.graphrag.port;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import com.orgmemory.graphrag.model.CanonicalEntity;
import com.orgmemory.graphrag.model.ContributionEmbedding;
import com.orgmemory.graphrag.model.EntityContribution;
import com.orgmemory.graphrag.model.EvidenceProvenance;
import com.orgmemory.graphrag.model.EvidenceReference;
import com.orgmemory.graphrag.model.FloatVector;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GraphProjectionManifestTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID ASSET_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000002");
    private static final UUID REVISION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000003");
    private static final UUID CHUNK_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000004");
    private static final UUID ACL_SNAPSHOT_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000005");
    private static final UUID ENTITY_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000006");
    private static final UUID CONTRIBUTION_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000007");
    private static final UUID EMBEDDING_PROFILE_ID =
            UUID.fromString("10000000-0000-0000-0000-000000000008");

    @Test
    void replayTimestampDoesNotChangeTheSemanticManifest() {
        GraphRevisionProjection first =
                projection("Evidence-backed policy", Instant.parse("2026-07-24T00:00:00Z"));
        GraphRevisionProjection replay =
                projection("Evidence-backed policy", Instant.parse("2026-07-24T00:05:00Z"));

        assertEquals(first.manifestFingerprint(), replay.manifestFingerprint());
        assertEquals(first.idempotencyKey(), replay.idempotencyKey());
    }

    @Test
    void outputChangeDoesChangeTheSemanticManifest() {
        GraphRevisionProjection first =
                projection("Evidence-backed policy", Instant.parse("2026-07-24T00:00:00Z"));
        GraphRevisionProjection changed =
                projection("Different policy", Instant.parse("2026-07-24T00:00:00Z"));

        assertNotEquals(first.manifestFingerprint(), changed.manifestFingerprint());
    }

    private static GraphRevisionProjection projection(
            String description, Instant extractedAt) {
        var provenance = new EvidenceProvenance(
                new EvidenceReference(
                        ORGANIZATION_ID,
                        ASSET_ID,
                        REVISION_ID,
                        CHUNK_ID,
                        ACL_SNAPSHOT_ID,
                        3),
                7,
                "openai",
                "gpt-5.6-sol",
                "lightrag-v1.5.4",
                "0000000000000000000000000000000000000000000000000000000000000000",
                0.95,
                extractedAt);
        var entity = new EntityContribution(
                CONTRIBUTION_ID,
                new CanonicalEntity(ENTITY_ID, "leave policy"),
                "POLICY",
                description,
                provenance);
        var contributions = new GraphRevisionContributions(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                7,
                List.of(entity),
                List.of());
        var embeddings = new GraphRevisionEmbeddings(
                ORGANIZATION_ID,
                ASSET_ID,
                REVISION_ID,
                7,
                EMBEDDING_PROFILE_ID,
                3,
                List.of(new ContributionEmbedding(
                        CONTRIBUTION_ID, new FloatVector(new float[] {0.1F, 0.2F, 0.3F}))),
                List.of());
        return new GraphRevisionProjection(contributions, embeddings);
    }
}
