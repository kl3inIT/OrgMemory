package com.orgmemory.core.assetregistry;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.error.BusinessException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** Coordinates stateless GitHub preview and independent per-Skill imports. */
@Service
public class SkillGitHubImportService {

    private static final int MAX_SKILLS = 20;

    private final SkillGitHubSourcePort source;
    private final SkillRegistryService skills;
    private final AssetRegistryService assets;

    SkillGitHubImportService(
            SkillGitHubSourcePort source,
            SkillRegistryService skills,
            AssetRegistryService assets) {
        this.source = source;
        this.skills = skills;
        this.assets = assets;
    }

    public List<SkillGitHubSourcePort.ConnectionOption> availableConnections(CurrentActor actor) {
        Objects.requireNonNull(actor, "actor");
        return source.availableConnections(actor.organizationId());
    }

    public Preview preview(CurrentActor actor, SourceRequest request) {
        Objects.requireNonNull(actor, "actor");
        SkillGitHubSourcePort.FetchResult fetched = source.fetch(fetchRequest(actor, request));
        List<PreviewItem> items = new ArrayList<>();
        for (SkillGitHubSourcePort.FetchedPackage candidate : fetched.packages()) {
            if (!candidate.importable()) {
                items.add(PreviewItem.invalid(
                        candidate.path(), candidate.errorCode(), candidate.errorMessage()));
                continue;
            }
            byte[] archive = candidate.archive();
            try {
                SkillPackageInspection inspection = skills.inspectPackage(
                        actor,
                        archive.length,
                        new ByteArrayInputStream(archive));
                items.add(PreviewItem.importable(candidate.path(), inspection));
            } catch (BusinessException invalid) {
                items.add(PreviewItem.invalid(
                        candidate.path(), invalid.code(), invalid.getMessage()));
            } catch (RuntimeException invalid) {
                items.add(PreviewItem.invalid(
                        candidate.path(),
                        "skill.github-package-invalid",
                        "The repository Skill package could not be validated"));
            }
        }
        return new Preview(
                fetched.repository(), fetched.revision(), fetched.visibility(), items);
    }

    public ImportResult importSelected(CurrentActor actor, ImportRequest request) {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(request.source(), "source");
        Objects.requireNonNull(request.knowledgeSpaceId(), "knowledgeSpaceId");
        Objects.requireNonNull(request.classification(), "classification");
        assets.requireSkillCreate(actor, request.knowledgeSpaceId());
        Set<String> selected = normalizedSelection(request.paths());
        if (selected.isEmpty() || selected.size() > MAX_SKILLS) {
            throw new BusinessValidationException(
                    "skill.github-selection-invalid",
                    "Choose between 1 and 20 repository Skills to import");
        }
        if (request.source().revision() == null
                || !request.source().revision().matches("[0-9a-f]{40}")) {
            throw new BusinessValidationException(
                    "skill.github-revision-invalid",
                    "Import requires the full commit SHA returned by preview");
        }
        SkillGitHubSourcePort.FetchResult fetched = source.fetch(fetchRequest(actor, request.source()));
        if (!request.source().revision().equals(fetched.revision())) {
            throw new BusinessValidationException(
                    "skill.github-revision-mismatch",
                    "GitHub returned a different revision than the one selected for import");
        }
        Map<String, SkillGitHubSourcePort.FetchedPackage> candidates = fetched.packages().stream()
                .collect(Collectors.toMap(
                        SkillGitHubSourcePort.FetchedPackage::path,
                        candidate -> candidate,
                        (left, right) -> left,
                        LinkedHashMap::new));
        List<ImportItem> results = new ArrayList<>();
        for (String path : selected) {
            SkillGitHubSourcePort.FetchedPackage candidate = candidates.get(path);
            if (candidate == null) {
                results.add(ImportItem.failed(
                        path,
                        "skill.github-path-not-found",
                        "The selected Skill path does not exist at this revision"));
                continue;
            }
            if (!candidate.importable()) {
                results.add(ImportItem.failed(
                        path, candidate.errorCode(), candidate.errorMessage()));
                continue;
            }
            byte[] archive = candidate.archive();
            try {
                AssetView asset = skills.importPackage(
                        actor,
                        request.namespace(),
                        request.knowledgeSpaceId(),
                        request.classification(),
                        archive.length,
                        new ByteArrayInputStream(archive),
                        new SkillPackageSpec.Origin(
                                fetched.repository(),
                                fetched.revision(),
                                candidate.path(),
                                fetched.visibility()));
                results.add(ImportItem.imported(path, asset));
            } catch (BusinessException failure) {
                results.add(ImportItem.failed(path, failure.code(), failure.getMessage()));
            } catch (RuntimeException failure) {
                results.add(ImportItem.failed(
                        path,
                        "skill.github-import-failed",
                        "The Skill could not be imported"));
            }
        }
        return new ImportResult(
                fetched.repository(), fetched.revision(), fetched.visibility(), results);
    }

    private static SkillGitHubSourcePort.FetchRequest fetchRequest(
            CurrentActor actor, SourceRequest request) {
        Objects.requireNonNull(request, "request");
        return new SkillGitHubSourcePort.FetchRequest(
                actor.organizationId(),
                actor.userId(),
                request.repository(),
                request.revision(),
                request.subpath(),
                request.connectionKey());
    }

    private static Set<String> normalizedSelection(List<String> paths) {
        if (paths == null) {
            return Set.of();
        }
        return paths.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(path -> !path.isEmpty())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }

    public record SourceRequest(
            String repository,
            String revision,
            String subpath,
            String connectionKey) {
    }

    public record ImportRequest(
            SourceRequest source,
            List<String> paths,
            String namespace,
            UUID knowledgeSpaceId,
            KnowledgeClassification classification) {
    }

    public record Preview(
            String repository,
            String revision,
            SkillPackageSpec.Visibility visibility,
            List<PreviewItem> skills) {
    }

    public record PreviewItem(
            String path,
            boolean importable,
            String name,
            String description,
            int fileCount,
            String errorCode,
            String errorMessage) {

        static PreviewItem importable(String path, SkillPackageInspection inspection) {
            return new PreviewItem(
                    path,
                    true,
                    inspection.name(),
                    inspection.description(),
                    inspection.files().size(),
                    "",
                    "");
        }

        static PreviewItem invalid(String path, String code, String message) {
            return new PreviewItem(path, false, "", "", 0, code, message);
        }
    }

    public record ImportResult(
            String repository,
            String revision,
            SkillPackageSpec.Visibility visibility,
            List<ImportItem> skills) {
    }

    public record ImportItem(
            String path,
            boolean imported,
            AssetView asset,
            String errorCode,
            String errorMessage) {

        static ImportItem imported(String path, AssetView asset) {
            return new ImportItem(path, true, asset, "", "");
        }

        static ImportItem failed(String path, String code, String message) {
            return new ImportItem(path, false, null, code, message);
        }
    }
}
