package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.retrieval.KnowledgeCatalogService;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CapabilityPackServiceTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID PACK_ASSET_ID = UUID.randomUUID();
    private static final UUID PACK_RELEASE_ID = UUID.randomUUID();
    private static final UUID COMPONENT_ASSET_ID =
            UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa");
    private static final UUID COMPONENT_RELEASE_ID =
            UUID.fromString("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, null, "User", "user@example.test");

    @Test
    void deniedComponentsCollapseIntoOneOpaqueAccessGap() {
        AssetRegistryService assets = mock(AssetRegistryService.class);
        KnowledgeCatalogService knowledge = mock(KnowledgeCatalogService.class);
        PackAssignmentRepository assignments = mock(PackAssignmentRepository.class);
        PackProgressRepository progress = mock(PackProgressRepository.class);
        CapabilityPackService service = new CapabilityPackService(
                assets,
                new CapabilityPackProfile(),
                knowledge,
                assignments,
                progress);
        PackAssignment assignment = mock(PackAssignment.class);
        UUID assignmentId = UUID.randomUUID();
        when(assignment.getId()).thenReturn(assignmentId);
        when(assignment.getStatus()).thenReturn(PackAssignmentStatus.IN_PROGRESS);
        when(assignment.getStartedAt()).thenReturn(Instant.now());
        when(assets.releaseForUse(
                        ACTOR,
                        PACK_ASSET_ID,
                        PACK_RELEASE_ID,
                        AssetType.CAPABILITY_PACK))
                .thenReturn(packRelease(twoItemPackPayload()));
        when(assignments.findByOrganizationIdAndPackReleaseIdAndActorUserId(
                        ORGANIZATION_ID, PACK_RELEASE_ID, USER_ID))
                .thenReturn(Optional.of(assignment));
        when(progress.findByOrganizationIdAndAssignmentIdOrderByItemKey(
                        ORGANIZATION_ID, assignmentId))
                .thenReturn(List.of());
        when(assets.releaseForUse(
                        ACTOR, COMPONENT_ASSET_ID, COMPONENT_RELEASE_ID))
                .thenReturn(componentRelease());
        when(knowledge.findExactVisible(ACTOR, UUID.fromString(
                        "cccccccc-cccc-4ccc-8ccc-cccccccccccc"), UUID.fromString(
                        "dddddddd-dddd-4ddd-8ddd-dddddddddddd")))
                .thenReturn(Optional.empty());

        CapabilityPackDefinition definition = service.describe(
                ACTOR, PACK_ASSET_ID, PACK_RELEASE_ID);
        PackJourney journey = service.get(
                ACTOR, PACK_ASSET_ID, PACK_RELEASE_ID);

        assertTrue(definition.accessGap());
        assertEquals(1, definition.items().size());
        assertEquals(
                COMPONENT_ASSET_ID,
                definition.items().getFirst().resourceId());
        assertEquals(
                COMPONENT_RELEASE_ID,
                definition.items().getFirst().pinnedVersionId());
        assertTrue(journey.accessGap());
        assertEquals(1, journey.items().size());
        assertEquals("Triage Prompt", journey.items().getFirst().title());
    }

    private static AssetConsumptionRelease packRelease(String payload) {
        return new AssetConsumptionRelease(
                PACK_ASSET_ID,
                PACK_RELEASE_ID,
                UUID.randomUUID(),
                AssetType.CAPABILITY_PACK,
                "support",
                "onboarding",
                "1",
                AssetPublicationMode.REVIEWED,
                "L1 onboarding",
                "Support onboarding",
                "INTERNAL",
                "1",
                payload,
                "e".repeat(64),
                AssetAvailability.AVAILABLE,
                Instant.now());
    }

    private static AssetConsumptionRelease componentRelease() {
        return new AssetConsumptionRelease(
                COMPONENT_ASSET_ID,
                COMPONENT_RELEASE_ID,
                UUID.randomUUID(),
                AssetType.PROMPT_TEMPLATE,
                "support",
                "triage",
                "1",
                AssetPublicationMode.REVIEWED,
                "Triage Prompt",
                "Triage",
                "INTERNAL",
                "1",
                AssetProfileValidationTests.promptPayload("{{ticket_text}}"),
                "f".repeat(64),
                AssetAvailability.AVAILABLE,
                Instant.now());
    }

    private static String twoItemPackPayload() {
        return """
                {
                  "purpose": "ROLE_ONBOARDING",
                  "audience": "L1 support",
                  "prerequisites": ["Active account"],
                  "expectedOutcome": "Agent can triage",
                  "items": [
                    {
                      "key": "prompt",
                      "required": true,
                      "kind": "REGISTRY_RELEASE",
                      "assetId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                      "releaseId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                      "knowledgeAssetId": null,
                      "knowledgeVersionId": null
                    },
                    {
                      "key": "policy",
                      "required": true,
                      "kind": "KNOWLEDGE_VERSION",
                      "assetId": null,
                      "releaseId": null,
                      "knowledgeAssetId": "cccccccc-cccc-4ccc-8ccc-cccccccccccc",
                      "knowledgeVersionId": "dddddddd-dddd-4ddd-8ddd-dddddddddddd"
                    }
                  ],
                  "completionCriteria": ["Required items complete"],
                  "reviewDate": "2026-12-31",
                  "owner": "Support operations"
                }
                """;
    }
}
