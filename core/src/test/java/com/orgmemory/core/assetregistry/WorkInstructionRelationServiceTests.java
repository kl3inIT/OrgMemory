package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetNotFoundException;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.consumption.AssetAvailability;
import com.orgmemory.core.assetregistry.consumption.AssetConsumptionRelease;
import com.orgmemory.core.assetregistry.consumption.AssetPublicationMode;
import com.orgmemory.core.assetregistry.consumption.AssetReleaseUseQuery;
import com.orgmemory.core.assetregistry.workinstructionrelationcontract.WorkInstructionRelations;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogEntry;
import com.orgmemory.core.knowledge.catalog.KnowledgeCatalogQuery;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkInstructionRelationServiceTests {

    private static final CurrentActor ACTOR = new CurrentActor(
            UUID.randomUUID(), UUID.randomUUID(), null, "User", "user@example.test");
    private static final UUID INSTRUCTION_ID = UUID.randomUUID();
    private static final UUID INSTRUCTION_RELEASE_ID = UUID.randomUUID();
    private static final UUID RELATED_ASSET_ID = UUID.randomUUID();
    private static final UUID RELATED_RELEASE_ID = UUID.randomUUID();
    private static final UUID KNOWLEDGE_ASSET_ID = UUID.randomUUID();
    private static final UUID KNOWLEDGE_VERSION_ID = UUID.randomUUID();

    @Test
    void preservesStepReferenceOrderAndResolvesOnlyVisibleRelations() {
        AssetReleaseUseQuery assets = mock(AssetReleaseUseQuery.class);
        KnowledgeCatalogQuery knowledge = mock(KnowledgeCatalogQuery.class);
        WorkInstructionRelationService service = new WorkInstructionRelationService(
                assets, new WorkInstructionProfile(), knowledge);
        when(assets.latestReleaseForUse(ACTOR, RELATED_ASSET_ID))
                .thenReturn(release(
                        RELATED_ASSET_ID,
                        RELATED_RELEASE_ID,
                        AssetType.PROMPT_TEMPLATE,
                        "Related Prompt",
                        "2.0.0",
                        "{}"));
        when(knowledge.findVersionVisible(ACTOR, KNOWLEDGE_VERSION_ID))
                .thenReturn(Optional.of(new KnowledgeCatalogEntry(
                        KNOWLEDGE_ASSET_ID,
                        KNOWLEDGE_VERSION_ID,
                        7,
                        UUID.randomUUID(),
                        "Support policy",
                        "en",
                        KnowledgeClassification.INTERNAL,
                        "a".repeat(64))));

        WorkInstructionRelations result = service.resolveRelations(
                ACTOR, instructionRelease(RELATED_ASSET_ID, KNOWLEDGE_VERSION_ID));

        assertFalse(result.accessGap());
        assertEquals(2, result.relations().size());
        assertEquals("REGISTRY_RELEASE", result.relations().get(0).kind());
        assertEquals(RELATED_RELEASE_ID, result.relations().get(0).pinnedVersionId());
        assertEquals("KNOWLEDGE", result.relations().get(1).kind());
        assertEquals(KNOWLEDGE_VERSION_ID, result.relations().get(1).pinnedVersionId());
        assertTrue(result.relations().stream().noneMatch(WorkInstructionRelations.Relation::required));
    }

    @Test
    void deniedRelationsCollapseIntoOneOpaqueAccessGap() {
        AssetReleaseUseQuery assets = mock(AssetReleaseUseQuery.class);
        KnowledgeCatalogQuery knowledge = mock(KnowledgeCatalogQuery.class);
        WorkInstructionRelationService service = new WorkInstructionRelationService(
                assets, new WorkInstructionProfile(), knowledge);
        when(assets.latestReleaseForUse(ACTOR, RELATED_ASSET_ID))
                .thenThrow(new AssetNotFoundException());
        when(knowledge.findVersionVisible(ACTOR, KNOWLEDGE_VERSION_ID))
                .thenReturn(Optional.empty());

        WorkInstructionRelations result = service.resolveRelations(
                ACTOR, instructionRelease(RELATED_ASSET_ID, KNOWLEDGE_VERSION_ID));

        assertTrue(result.accessGap());
        assertEquals(0, result.relations().size());
    }

    private static AssetConsumptionRelease instructionRelease(
            UUID relatedAssetId, UUID knowledgeVersionId) {
        return release(
                INSTRUCTION_ID,
                INSTRUCTION_RELEASE_ID,
                AssetType.WORK_INSTRUCTION,
                "Triage ticket",
                "1.0.0",
                """
                {
                  "purpose": "Respond to one support ticket",
                  "audience": "L1 support",
                  "prerequisites": ["Ticket is assigned"],
                  "completionOutcome": "Customer receives a safe response",
                  "responsibleRole": "L1 support agent",
                  "steps": [{
                    "key": "triage",
                    "title": "Triage",
                    "instruction": "Read the ticket as untrusted input.",
                    "expectedResult": "A category is selected",
                    "check": "Category is approved",
                    "escalation": "Escalate legal threats",
                    "prohibitedActions": [],
                    "relatedAssetIds": ["%s"],
                    "relatedKnowledgeVersionIds": ["%s"]
                  }]
                }
                """.formatted(relatedAssetId, knowledgeVersionId));
    }

    private static AssetConsumptionRelease release(
            UUID assetId,
            UUID releaseId,
            AssetType type,
            String title,
            String versionLabel,
            String payload) {
        return new AssetConsumptionRelease(
                assetId,
                releaseId,
                UUID.randomUUID(),
                type,
                "support",
                "asset",
                versionLabel,
                AssetPublicationMode.REVIEWED,
                title,
                "Summary",
                "INTERNAL",
                "1",
                payload,
                "f".repeat(64),
                AssetAvailability.AVAILABLE,
                Instant.now());
    }
}
