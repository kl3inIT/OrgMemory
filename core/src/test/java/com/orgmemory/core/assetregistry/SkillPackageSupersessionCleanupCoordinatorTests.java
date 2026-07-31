package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SkillPackageSupersessionCleanupCoordinatorTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID ASSET_ID = UUID.randomUUID();

    @Test
    void deletesAnUnreferencedSupersededObjectThenResolvesTheLedgerRow() {
        Fixture fixture = new Fixture();
        when(fixture.supersessions.findForUpdate(fixture.item.getId()))
                .thenReturn(Optional.of(fixture.item));
        when(fixture.references.existsByOrganizationIdAndReferenceValue(
                        ORGANIZATION_ID, "old-key"))
                .thenReturn(false);

        assertEquals(
                SkillPackageCleanupOutcome.DELETED,
                fixture.coordinator.cleanup(fixture.item.getId()));

        verify(fixture.storage).delete("old-key");
        verify(fixture.supersessions).delete(fixture.item);
    }

    @Test
    void retainsAnObjectPinnedByAnImmutableReferenceAndResolvesTheLedgerRow() {
        Fixture fixture = new Fixture();
        when(fixture.supersessions.findForUpdate(fixture.item.getId()))
                .thenReturn(Optional.of(fixture.item));
        when(fixture.references.existsByOrganizationIdAndReferenceValue(
                        ORGANIZATION_ID, "old-key"))
                .thenReturn(true);

        assertEquals(
                SkillPackageCleanupOutcome.RETAINED_BY_IMMUTABLE_REFERENCE,
                fixture.coordinator.cleanup(fixture.item.getId()));

        verify(fixture.storage, never()).delete("old-key");
        verify(fixture.supersessions).delete(fixture.item);
    }

    @Test
    void recordsAStorageFailureForBoundedRetryWithoutLosingTheLedgerRow() {
        Fixture fixture = new Fixture();
        when(fixture.supersessions.findForUpdate(fixture.item.getId()))
                .thenReturn(Optional.of(fixture.item));
        when(fixture.references.existsByOrganizationIdAndReferenceValue(
                        ORGANIZATION_ID, "old-key"))
                .thenReturn(false);
        org.mockito.Mockito.doThrow(new IllegalStateException("storage unavailable"))
                .when(fixture.storage)
                .delete("old-key");

        assertEquals(
                SkillPackageCleanupOutcome.RETRY_SCHEDULED,
                fixture.coordinator.cleanup(fixture.item.getId()));

        assertEquals(1, fixture.item.getAttemptCount());
        verify(fixture.supersessions).save(fixture.item);
        verify(fixture.supersessions, never()).delete(fixture.item);
    }

    private static final class Fixture {
        private final SkillPackageSupersessionRepository supersessions =
                mock(SkillPackageSupersessionRepository.class);
        private final AssetPayloadReferenceRepository references =
                mock(AssetPayloadReferenceRepository.class);
        private final SkillPackageStoragePort storage =
                mock(SkillPackageStoragePort.class);
        private final SkillPackageSupersessionCleanupCoordinator coordinator =
                new SkillPackageSupersessionCleanupCoordinator(
                        supersessions, references, storage);
        private final SkillPackageSupersession item = new SkillPackageSupersession(
                ORGANIZATION_ID, ASSET_ID, "old-key", "new-key", Instant.now());
    }
}
