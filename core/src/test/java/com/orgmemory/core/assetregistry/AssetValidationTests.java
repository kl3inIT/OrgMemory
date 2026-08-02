package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetValidationTests {

    @Test
    void coordinatesRejectValuesLongerThanTheirDatabaseColumns() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new Asset(
                        UUID.randomUUID(),
                        AssetType.PROMPT_TEMPLATE,
                        "a".repeat(129),
                        "valid-slug",
                        UUID.randomUUID()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new Asset(
                        UUID.randomUUID(),
                        AssetType.PROMPT_TEMPLATE,
                        "valid.namespace",
                        "a".repeat(129),
                        UUID.randomUUID()));
    }

    @Test
    void releaseLabelRejectsValuesLongerThanItsDatabaseColumn() {
        UUID actorId = UUID.randomUUID();
        AssetDraft draft = new AssetDraft(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Title",
                "Summary",
                "INTERNAL",
                "1",
                "{\"task\":\"triage\"}",
                actorId);
        AssetRevision revision = new AssetRevision(
                draft,
                1,
                new AssetPayloadDigester().canonicalize(
                        draft.getTitle(),
                        draft.getSummary(),
                        draft.getClassification(),
                        draft.getSchemaVersion(),
                        draft.getPayload()),
                "Initial",
                actorId);

        assertThrows(
                IllegalArgumentException.class,
                () -> new AssetRelease(
                        revision,
                        1,
                        "a".repeat(65),
                        AssetPublicationMode.REVIEWED,
                        actorId,
                        Instant.parse("2026-07-25T00:00:00Z")));
    }
}
