package com.orgmemory.api.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.AssetView;
import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.skill.SkillGitHubOperations;
import com.orgmemory.core.assetregistry.skill.SkillGitHubSourcePort;
import com.orgmemory.core.assetregistry.skill.SkillPackageOperations;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class AssetRegistryControllerTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("89000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("89000000-0000-0000-0000-000000000002");
    private static final UUID SPACE_ID =
            UUID.fromString("89000000-0000-0000-0000-000000000003");
    private static final UUID AVAILABLE_ID =
            UUID.fromString("89000000-0000-0000-0000-000000000004");
    private static final UUID PENDING_ID =
            UUID.fromString("89000000-0000-0000-0000-000000000005");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "Skill editor",
            "skill.editor@example.test");

    @Test
    void githubImportKeepsEveryItemWhenOneImportedAssetCannotBeResolved() {
        AssetRegistryService assets = mock(AssetRegistryService.class);
        SkillGitHubOperations github = mock(SkillGitHubOperations.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        AssetView available = new AssetView(
                AVAILABLE_ID,
                AssetType.SKILL,
                "support",
                "triage",
                SPACE_ID,
                null,
                true,
                ACTOR.userId(),
                com.orgmemory.core.assetregistry.api.AssetSharingState.PRIVATE,
                null,
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of());
        when(actors.current(authentication)).thenReturn(ACTOR);
        when(github.importSelected(any(), any())).thenReturn(
                new SkillGitHubOperations.ImportResult(
                        "acme/skills",
                        "a".repeat(40),
                        SkillGitHubSourcePort.Visibility.PUBLIC,
                        List.of(
                                new SkillGitHubOperations.ImportItem(
                                        "skills/triage/SKILL.md",
                                        true,
                                        AVAILABLE_ID,
                                        "",
                                        ""),
                                new SkillGitHubOperations.ImportItem(
                                        "skills/pending/SKILL.md",
                                        true,
                                        PENDING_ID,
                                        "",
                                        ""),
                                new SkillGitHubOperations.ImportItem(
                                        "skills/duplicate/SKILL.md",
                                        false,
                                        null,
                                        "asset.conflict",
                                        "An Asset already uses this coordinate"))));
        when(assets.get(ACTOR, AVAILABLE_ID)).thenReturn(available);
        when(assets.get(ACTOR, PENDING_ID))
                .thenThrow(new AssetUnavailableException("Projection pending"));
        AssetRegistryController controller = new AssetRegistryController(
                assets,
                mock(SkillPackageOperations.class),
                github,
                mock(com.orgmemory.core.assetregistry.skill.SkillActivationOperations.class),
                actors);

        AssetRegistryController.ImportResult result = controller.importGitHubSkills(
                new AssetRegistryController.GitHubSkillImportRequest(
                        new AssetRegistryController.GitHubSkillSourceRequest(
                                "acme/skills", "a".repeat(40), "skills", "", SPACE_ID),
                        List.of(
                                "skills/triage/SKILL.md",
                                "skills/pending/SKILL.md",
                                "skills/duplicate/SKILL.md"),
                        "support",
                        KnowledgeClassification.INTERNAL),
                authentication);

        assertEquals(3, result.skills().size());
        assertSame(available, result.skills().getFirst().asset());
        AssetRegistryController.ImportItem pending = result.skills().get(1);
        assertTrue(pending.imported());
        assertNull(pending.asset());
        assertEquals("asset.unavailable", pending.errorCode());
        assertEquals("Projection pending", pending.errorMessage());
        AssetRegistryController.ImportItem duplicate = result.skills().get(2);
        assertFalse(duplicate.imported());
        assertEquals("asset.conflict", duplicate.errorCode());
    }
}
