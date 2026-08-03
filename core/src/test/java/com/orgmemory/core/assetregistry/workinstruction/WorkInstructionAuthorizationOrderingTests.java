package com.orgmemory.core.assetregistry.workinstruction;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.consumption.AssetAvailability;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetPublicationMode;
import com.orgmemory.core.assetregistry.consumption.AssetReleaseUseQuery;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkInstructionAuthorizationOrderingTests {

    private static final UUID ASSET_ID = UUID.randomUUID();
    private static final UUID RELEASE_ID = UUID.randomUUID();
    private static final CurrentActor ACTOR = new CurrentActor(
            UUID.randomUUID(), UUID.randomUUID(), null, "User", "user@example.test");

    @Test
    void authorizationDenialHappensBeforeAcknowledgementPersistence() {
        AssetReleaseUseQuery releases = mock(AssetReleaseUseQuery.class);
        WorkInstructionProfile profile = mock(WorkInstructionProfile.class);
        WorkInstructionAcknowledgementRepository acknowledgements =
                mock(WorkInstructionAcknowledgementRepository.class);
        when(releases.workInstructionForUse(ACTOR, ASSET_ID, RELEASE_ID))
                .thenThrow(new AssetNotFoundException());
        WorkInstructionService service = new WorkInstructionService(
                releases, profile, acknowledgements);

        assertThrows(
                AssetNotFoundException.class,
                () -> service.acknowledge(ACTOR, ASSET_ID, RELEASE_ID));

        verifyNoInteractions(profile, acknowledgements);
    }

    @Test
    void wrongAssetTypeStaysOpaqueAndDoesNotTouchPersistence() {
        AssetReleaseUseQuery releases = new AssetReleaseUseQuery() {
            @Override
            public AssetConsumptionRelease releaseForUse(
                    CurrentActor actor, UUID assetId, UUID releaseId) {
                return promptRelease();
            }

            @Override
            public AssetConsumptionRelease latestReleaseForUse(
                    CurrentActor actor, UUID assetId) {
                return promptRelease();
            }
        };
        WorkInstructionProfile profile = mock(WorkInstructionProfile.class);
        WorkInstructionAcknowledgementRepository acknowledgements =
                mock(WorkInstructionAcknowledgementRepository.class);
        WorkInstructionService service = new WorkInstructionService(
                releases, profile, acknowledgements);

        assertThrows(
                AssetNotFoundException.class,
                () -> service.follow(ACTOR, ASSET_ID, RELEASE_ID));

        verifyNoInteractions(profile, acknowledgements);
    }

    private static AssetConsumptionRelease promptRelease() {
        return new AssetConsumptionRelease(
                ASSET_ID,
                RELEASE_ID,
                UUID.randomUUID(),
                AssetType.PROMPT_TEMPLATE,
                "support",
                "prompt",
                "1.0.0",
                AssetPublicationMode.REVIEWED,
                "Prompt",
                "Summary",
                "INTERNAL",
                "1",
                "{}",
                "a".repeat(64),
                AssetAvailability.AVAILABLE,
                Instant.now());
    }
}
