package com.orgmemory.core.assetregistry.skill;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.api.AssetConflictException;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageAssetCommand;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SkillGitHubImportServiceTests {

    private static final CurrentActor ACTOR = new CurrentActor(
            UUID.fromString("11111111-1111-4111-8111-111111111111"),
            UUID.fromString("22222222-2222-4222-8222-222222222222"),
            UUID.fromString("33333333-3333-4333-8333-333333333333"),
            "Skill owner",
            "owner@example.test");
    private static final UUID SPACE_ID =
            UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final String SHA = "a".repeat(40);

    @Test
    void previewKeepsValidAndInvalidCandidatesInOneResult() {
        SkillGitHubSourcePort source = mock(SkillGitHubSourcePort.class);
        SkillRegistryService skills = mock(SkillRegistryService.class);
        when(source.fetch(any())).thenReturn(new SkillGitHubSourcePort.FetchResult(
                "acme/skills",
                SHA,
                SkillGitHubSourcePort.Visibility.PUBLIC,
                List.of(
                        valid("skills/triage/SKILL.md", 1),
                        new SkillGitHubSourcePort.FetchedPackage(
                                "skills/large/SKILL.md",
                                null,
                                "skill.github-package-too-large",
                                "Too large"))));
        when(skills.inspectPackage(eq(ACTOR), eq(1L), any(InputStream.class)))
                .thenReturn(inspection("triage"));
        SkillPackageAssetCommand packages = mock(SkillPackageAssetCommand.class);
        SkillGitHubImportService service = new SkillGitHubImportService(
                source, skills, packages);

        SkillGitHubOperations.Preview preview = service.preview(
                ACTOR,
                new SkillGitHubOperations.SourceRequest(
                        "acme/skills", "main", "skills", "", SPACE_ID));

        assertEquals(SHA, preview.revision());
        assertEquals(List.of(true, false),
                preview.skills().stream()
                        .map(SkillGitHubOperations.PreviewItem::importable)
                        .toList());
        assertEquals("triage", preview.skills().getFirst().name());
        assertEquals(
                "skill.github-package-too-large",
                preview.skills().get(1).errorCode());
        verify(packages).requireCreate(ACTOR, SPACE_ID);
    }

    @Test
    void importsEachSelectedSkillIndependentlyAndPreservesPinnedProvenance() {
        SkillGitHubSourcePort source = mock(SkillGitHubSourcePort.class);
        SkillRegistryService skills = mock(SkillRegistryService.class);
        SkillPackageAssetCommand packages = mock(SkillPackageAssetCommand.class);
        doNothing().when(packages).requireCreate(ACTOR, SPACE_ID);
        String firstPath = "skills/triage/SKILL.md";
        String secondPath = "skills/reply/SKILL.md";
        when(source.fetch(any())).thenReturn(new SkillGitHubSourcePort.FetchResult(
                "acme/skills",
                SHA,
                SkillGitHubSourcePort.Visibility.PRIVATE,
                List.of(valid(firstPath, 1), valid(secondPath, 2))));
        UUID importedId = UUID.randomUUID();
        when(skills.importPackage(
                        eq(ACTOR),
                        eq("support"),
                        eq(SPACE_ID),
                        eq(KnowledgeClassification.INTERNAL),
                        anyLong(),
                        any(InputStream.class),
                        any(SkillPackageSpec.Origin.class)))
                .thenReturn(importedId)
                .thenThrow(new AssetConflictException("Duplicate"));
        SkillGitHubImportService service = new SkillGitHubImportService(
                source, skills, packages);

        SkillGitHubOperations.ImportResult result = service.importSelected(
                ACTOR,
                new SkillGitHubOperations.ImportRequest(
                        new SkillGitHubOperations.SourceRequest(
                                "acme/skills", SHA, "skills", "private-app", SPACE_ID),
                        List.of(firstPath, secondPath),
                        "support",
                        KnowledgeClassification.INTERNAL));

        assertEquals(List.of(true, false),
                result.skills().stream()
                        .map(SkillGitHubOperations.ImportItem::imported)
                        .toList());
        assertEquals(importedId, result.skills().getFirst().assetId());
        assertEquals("asset.conflict", result.skills().get(1).errorCode());
        ArgumentCaptor<SkillPackageSpec.Origin> origins =
                ArgumentCaptor.forClass(SkillPackageSpec.Origin.class);
        verify(skills, org.mockito.Mockito.times(2)).importPackage(
                eq(ACTOR),
                eq("support"),
                eq(SPACE_ID),
                eq(KnowledgeClassification.INTERNAL),
                anyLong(),
                any(InputStream.class),
                origins.capture());
        assertEquals(List.of(firstPath, secondPath),
                origins.getAllValues().stream().map(SkillPackageSpec.Origin::path).toList());
        assertEquals(SkillGitHubSourcePort.Visibility.PRIVATE,
                origins.getAllValues().getFirst().visibility());
    }

    @Test
    void connectionDiscoveryRequiresSkillCreatePermissionForTheSelectedSpace() {
        SkillGitHubSourcePort source = mock(SkillGitHubSourcePort.class);
        SkillPackageAssetCommand packages = mock(SkillPackageAssetCommand.class);
        when(source.availableConnections(ACTOR.organizationId())).thenReturn(List.of());
        SkillGitHubImportService service = new SkillGitHubImportService(
                source, mock(SkillRegistryService.class), packages);

        service.availableConnections(ACTOR, SPACE_ID);

        verify(packages).requireCreate(ACTOR, SPACE_ID);
        verify(source).availableConnections(ACTOR.organizationId());
    }

    @Test
    void importRejectsNonCommitRevisionBeforeFetchingRepository() {
        SkillGitHubSourcePort source = mock(SkillGitHubSourcePort.class);
        SkillGitHubImportService service = new SkillGitHubImportService(
                source,
                mock(SkillRegistryService.class),
                mock(SkillPackageAssetCommand.class));

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> service.importSelected(ACTOR, request("main", List.of("SKILL.md"))));

        assertEquals("skill.github-revision-invalid", failure.code());
        verify(source, never()).fetch(any());
    }

    @Test
    void importRejectsARevisionThatChangesAfterPreview() {
        SkillGitHubSourcePort source = mock(SkillGitHubSourcePort.class);
        when(source.fetch(any())).thenReturn(new SkillGitHubSourcePort.FetchResult(
                "acme/skills",
                "b".repeat(40),
                SkillGitHubSourcePort.Visibility.PUBLIC,
                List.of(valid("SKILL.md", 1))));
        SkillGitHubImportService service = new SkillGitHubImportService(
                source,
                mock(SkillRegistryService.class),
                mock(SkillPackageAssetCommand.class));

        BusinessValidationException failure = assertThrows(
                BusinessValidationException.class,
                () -> service.importSelected(ACTOR, request(SHA, List.of("SKILL.md"))));

        assertEquals("skill.github-revision-mismatch", failure.code());
    }

    @Test
    void importReportsASelectedPathMissingAtThePinnedRevision() {
        SkillGitHubSourcePort source = mock(SkillGitHubSourcePort.class);
        when(source.fetch(any())).thenReturn(new SkillGitHubSourcePort.FetchResult(
                "acme/skills", SHA, SkillGitHubSourcePort.Visibility.PUBLIC, List.of()));
        SkillRegistryService skills = mock(SkillRegistryService.class);
        SkillGitHubImportService service = new SkillGitHubImportService(
                source, skills, mock(SkillPackageAssetCommand.class));

        SkillGitHubOperations.ImportResult result =
                service.importSelected(ACTOR, request(SHA, List.of("removed/SKILL.md")));

        assertEquals("skill.github-path-not-found", result.skills().getFirst().errorCode());
        verify(skills, never()).importPackage(
                any(), any(), any(), any(), anyLong(), any(InputStream.class), any());
    }

    @Test
    void fetchRequestDefaultsAnOmittedRevisionToHead() {
        SkillGitHubSourcePort.FetchRequest request = new SkillGitHubSourcePort.FetchRequest(
                ACTOR.organizationId(), ACTOR.userId(), "acme/skills", null, null, null);

        assertEquals("HEAD", request.revision());
    }

    private static SkillGitHubOperations.ImportRequest request(
            String revision, List<String> paths) {
        return new SkillGitHubOperations.ImportRequest(
                new SkillGitHubOperations.SourceRequest(
                        "acme/skills", revision, "", "", SPACE_ID),
                paths,
                "engineering",
                KnowledgeClassification.INTERNAL);
    }

    private static SkillGitHubSourcePort.FetchedPackage valid(String path, int marker) {
        return new SkillGitHubSourcePort.FetchedPackage(
                path, new byte[] {(byte) marker}, "", "");
    }

    private static SkillPackageInspection inspection(String name) {
        return new SkillPackageInspection(
                name,
                "A repository Skill",
                "",
                "",
                "",
                Map.of(),
                "# Skill",
                "b".repeat(64),
                1,
                List.of(new SkillPackageInspection.FileEntry(
                        "SKILL.md", 1, "c".repeat(64))));
    }
}
