package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.consumption.AssetAvailability;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetPublicationMode;
import com.orgmemory.core.assetregistry.workinstructionrelationcontract.WorkInstructionRelationResolver;
import com.orgmemory.core.assetregistry.workinstructionrelationcontract.WorkInstructionRelations;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetDeliveryServiceWorkInstructionTests {

    @Test
    void delegatesOnlyAfterOneExactSourceReleaseResolutionAndMapsThePublicResult() {
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(), UUID.randomUUID(), null, "User", "user@example.test");
        UUID assetId = UUID.randomUUID();
        UUID releaseId = UUID.randomUUID();
        UUID relatedAssetId = UUID.randomUUID();
        UUID relatedReleaseId = UUID.randomUUID();
        AssetRegistryService assets = mock(AssetRegistryService.class);
        CapabilityPackService packs = mock(CapabilityPackService.class);
        WorkInstructionRelationResolver resolver = mock(WorkInstructionRelationResolver.class);
        AssetConsumptionRelease release = new AssetConsumptionRelease(
                assetId,
                releaseId,
                UUID.randomUUID(),
                AssetType.WORK_INSTRUCTION,
                "support",
                "instruction",
                "1.0.0",
                AssetPublicationMode.REVIEWED,
                "Triage ticket",
                "Summary",
                "INTERNAL",
                "1",
                "{}",
                "a".repeat(64),
                AssetAvailability.AVAILABLE,
                Instant.now());
        when(assets.releaseForUse(actor, assetId, releaseId)).thenReturn(release);
        when(resolver.resolveRelations(actor, release)).thenReturn(new WorkInstructionRelations(
                false,
                List.of(new WorkInstructionRelations.Relation(
                        "triage",
                        "REGISTRY_RELEASE",
                        relatedAssetId,
                        relatedReleaseId,
                        "Related Prompt",
                        "2.0.0",
                        false))));
        AssetDeliveryService service = new AssetDeliveryService(assets, packs, resolver);

        AssetRelationResolution result = service.resolveRelations(actor, assetId, releaseId);

        assertEquals(assetId, result.assetId());
        assertEquals(releaseId, result.releaseId());
        assertEquals(relatedReleaseId, result.relations().getFirst().pinnedVersionId());
        verify(assets).releaseForUse(actor, assetId, releaseId);
        verify(resolver).resolveRelations(actor, release);
        verifyNoMoreInteractions(assets, resolver, packs);
    }
}
