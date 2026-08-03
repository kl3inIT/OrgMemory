package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public interface SkillGitHubOperations {

    List<ConnectionOption> availableConnections(
            CurrentActor actor, UUID knowledgeSpaceId);

    Preview preview(CurrentActor actor, SourceRequest request);

    ImportResult importSelected(CurrentActor actor, ImportRequest request);

    record ConnectionOption(String key) {

        public ConnectionOption {
            key = Objects.requireNonNull(key, "key");
        }
    }

    record SourceRequest(
            String repository,
            String revision,
            String subpath,
            String connectionKey,
            UUID knowledgeSpaceId) {
    }

    record ImportRequest(
            SourceRequest source,
            List<String> paths,
            String namespace,
            KnowledgeClassification classification) {
    }

    record Preview(
            String repository,
            String revision,
            SkillGitHubSourcePort.Visibility visibility,
            List<PreviewItem> skills) {
    }

    record PreviewItem(
            String path,
            boolean importable,
            String name,
            String description,
            int fileCount,
            String errorCode,
            String errorMessage) {

        static PreviewItem importable(
                String path, SkillPackageInspection inspection) {
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

    record ImportResult(
            String repository,
            String revision,
            SkillGitHubSourcePort.Visibility visibility,
            List<ImportItem> skills) {
    }

    record ImportItem(
            String path,
            boolean imported,
            UUID assetId,
            String errorCode,
            String errorMessage) {

        static ImportItem imported(String path, UUID assetId) {
            return new ImportItem(path, true, assetId, "", "");
        }

        static ImportItem failed(String path, String code, String message) {
            return new ImportItem(path, false, null, code, message);
        }
    }
}
