package com.orgmemory.core.assetregistry;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Fetches bounded Skill packages from one exact GitHub repository revision. */
public interface SkillGitHubSourcePort {

    FetchResult fetch(FetchRequest request);

    List<ConnectionOption> availableConnections(UUID organizationId);

    record ConnectionOption(String key) {

        public ConnectionOption {
            key = Objects.requireNonNull(key, "key");
        }
    }

    record FetchRequest(
            UUID organizationId,
            UUID actorUserId,
            String repository,
            String revision,
            String subpath,
            String connectionKey) {

        public FetchRequest {
            Objects.requireNonNull(organizationId, "organizationId");
            Objects.requireNonNull(actorUserId, "actorUserId");
            repository = Objects.requireNonNull(repository, "repository").strip();
            revision = revision == null || revision.isBlank() ? "HEAD" : revision.strip();
            subpath = subpath == null ? "" : subpath.strip();
            connectionKey = connectionKey == null ? "" : connectionKey.strip();
        }
    }

    record FetchResult(
            String repository,
            String revision,
            SkillPackageSpec.Visibility visibility,
            List<FetchedPackage> packages) {

        public FetchResult {
            repository = Objects.requireNonNull(repository, "repository");
            revision = Objects.requireNonNull(revision, "revision");
            visibility = Objects.requireNonNull(visibility, "visibility");
            packages = List.copyOf(packages);
        }
    }

    record FetchedPackage(
            String path,
            byte[] archive,
            String errorCode,
            String errorMessage) {

        public FetchedPackage {
            path = Objects.requireNonNull(path, "path");
            archive = archive == null ? null : Arrays.copyOf(archive, archive.length);
            errorCode = errorCode == null ? "" : errorCode;
            errorMessage = errorMessage == null ? "" : errorMessage;
            if ((archive == null) == errorCode.isBlank()) {
                throw new IllegalArgumentException(
                        "A fetched Skill must contain either an archive or an error");
            }
        }

        @Override
        public byte[] archive() {
            return archive == null ? null : Arrays.copyOf(archive, archive.length);
        }

        public boolean importable() {
            return archive != null;
        }
    }
}
