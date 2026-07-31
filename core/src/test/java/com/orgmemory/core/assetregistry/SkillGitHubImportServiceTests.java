package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
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
                SkillPackageSpec.Visibility.PUBLIC,
                List.of(
                        valid("skills/triage/SKILL.md", 1),
                        new SkillGitHubSourcePort.FetchedPackage(
                                "skills/large/SKILL.md",
                                null,
                                "skill.github-package-too-large",
                                "Too large"))));
        when(skills.inspectPackage(eq(ACTOR), eq(1L), any(InputStream.class)))
                .thenReturn(inspection("triage"));
        SkillGitHubImportService service = new SkillGitHubImportService(
                source, skills, mock(AssetRegistryService.class));

        SkillGitHubImportService.Preview preview = service.preview(
                ACTOR,
                new SkillGitHubImportService.SourceRequest(
                        "acme/skills", "main", "skills", ""));

        assertEquals(SHA, preview.revision());
        assertEquals(List.of(true, false),
                preview.skills().stream()
                        .map(SkillGitHubImportService.PreviewItem::importable)
                        .toList());
        assertEquals("triage", preview.skills().getFirst().name());
        assertEquals(
                "skill.github-package-too-large",
                preview.skills().get(1).errorCode());
    }

    @Test
    void importsEachSelectedSkillIndependentlyAndPreservesPinnedProvenance() {
        SkillGitHubSourcePort source = mock(SkillGitHubSourcePort.class);
        SkillRegistryService skills = mock(SkillRegistryService.class);
        AssetRegistryService assets = mock(AssetRegistryService.class);
        doNothing().when(assets).requireSkillCreate(ACTOR, SPACE_ID);
        String firstPath = "skills/triage/SKILL.md";
        String secondPath = "skills/reply/SKILL.md";
        when(source.fetch(any())).thenReturn(new SkillGitHubSourcePort.FetchResult(
                "acme/skills",
                SHA,
                SkillPackageSpec.Visibility.PRIVATE,
                List.of(valid(firstPath, 1), valid(secondPath, 2))));
        AssetView imported = mock(AssetView.class);
        when(skills.importPackage(
                        eq(ACTOR),
                        eq("support"),
                        eq(SPACE_ID),
                        eq(KnowledgeClassification.INTERNAL),
                        anyLong(),
                        any(InputStream.class),
                        any(SkillPackageSpec.Origin.class)))
                .thenReturn(imported)
                .thenThrow(new AssetConflictException("Duplicate"));
        SkillGitHubImportService service = new SkillGitHubImportService(source, skills, assets);

        SkillGitHubImportService.ImportResult result = service.importSelected(
                ACTOR,
                new SkillGitHubImportService.ImportRequest(
                        new SkillGitHubImportService.SourceRequest(
                                "acme/skills", SHA, "skills", "private-app"),
                        List.of(firstPath, secondPath),
                        "support",
                        SPACE_ID,
                        KnowledgeClassification.INTERNAL));

        assertEquals(List.of(true, false),
                result.skills().stream()
                        .map(SkillGitHubImportService.ImportItem::imported)
                        .toList());
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
        assertEquals(SkillPackageSpec.Visibility.PRIVATE,
                origins.getAllValues().getFirst().visibility());
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
                List.of(new SkillPackageSpec.FileEntry(
                        "SKILL.md", 1, "c".repeat(64))));
    }
}
