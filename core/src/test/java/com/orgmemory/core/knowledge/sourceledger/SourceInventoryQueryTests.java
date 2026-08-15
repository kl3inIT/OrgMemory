package com.orgmemory.core.knowledge.sourceledger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceInventoryQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final String SOURCE_SYSTEM = "slack";
    private static final String CONNECTION_KEY = "workspace-1";

    private final SourceObjectRepository sources = mock(SourceObjectRepository.class);
    private final SourceInventoryQuery query = new SourceInventoryQuery(sources);

    @Test
    void exposesActiveObjectIdsAndAggregatedInventory() {
        SourceObject source = mock(SourceObject.class);
        Instant activeUpdatedAt = Instant.parse("2026-08-01T01:00:00Z");
        Instant archivedUpdatedAt = Instant.parse("2026-08-01T02:00:00Z");
        UUID sourceObjectId = UUID.randomUUID();
        when(sources.findByOrganizationIdAndSourceSystemAndSourceConnectionKeyAndExternalObjectId(
                        ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, "channel-1"))
                .thenReturn(java.util.Optional.of(source));
        when(source.getId()).thenReturn(sourceObjectId);
        when(sources.countActiveCurrentRevision(
                        ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, "channel-1"))
                .thenReturn(1L);
        when(sources.findActiveExternalObjectIds(
                        ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY))
                .thenReturn(List.of("channel-1", "channel-2"));
        when(sources.countByStatus(ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY))
                .thenReturn(List.of(
                        new SourceObjectStatusCount(
                                SourceObjectStatus.ACTIVE, 12L, activeUpdatedAt),
                        new SourceObjectStatusCount(
                                SourceObjectStatus.ARCHIVED, 3L, archivedUpdatedAt)));

        assertEquals(
                new SourceInventoryRef(sourceObjectId),
                query.find(
                                ORGANIZATION_ID,
                                SOURCE_SYSTEM,
                                CONNECTION_KEY,
                                "channel-1")
                        .orElseThrow());
        assertTrue(query.hasRetrievalSurface(
                ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY, "channel-1"));
        assertEquals(
                List.of("channel-1", "channel-2"),
                query.activeExternalObjectIds(
                        ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY));
        assertEquals(
                new SourceInventorySummary(12L, 3L, archivedUpdatedAt),
                query.summarize(ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY));
    }

    @Test
    void emptyInventoryHasZeroCountsAndNoLatestActivity() {
        when(sources.countByStatus(ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY))
                .thenReturn(List.of());

        assertEquals(
                new SourceInventorySummary(0L, 0L, null),
                query.summarize(ORGANIZATION_ID, SOURCE_SYSTEM, CONNECTION_KEY));
    }
}
