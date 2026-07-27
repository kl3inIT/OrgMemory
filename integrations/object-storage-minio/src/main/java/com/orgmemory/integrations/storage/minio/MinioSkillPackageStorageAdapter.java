package com.orgmemory.integrations.storage.minio;

import com.orgmemory.core.assetregistry.SkillPackageStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

final class MinioSkillPackageStorageAdapter implements SkillPackageStoragePort {

    private static final String MEDIA_TYPE = "application/zip";

    private final ObjectStoragePort objects;

    MinioSkillPackageStorageAdapter(ObjectStoragePort objects) {
        this.objects = objects;
    }

    @Override
    public StoredSkillPackage put(
            SkillPackageWriteRequest request, InputStream content) {
        ObjectKey key = new ObjectKey("assets/skills/"
                + request.organizationId()
                + "/"
                + request.packageId()
                + ".zip");
        Map<String, String> metadata = new LinkedHashMap<>(request.metadata());
        metadata.put("asset-kind", "skill-package");
        var stored = objects.put(
                new ObjectWriteRequest(
                        key, request.contentLength(), MEDIA_TYPE, Map.copyOf(metadata)),
                content);
        if (!request.expectedSha256().equals(stored.sha256())) {
            try {
                objects.delete(key);
            } catch (RuntimeException cleanupFailure) {
                var mismatch = new IllegalArgumentException(
                        "Stored Skill package digest does not match inspected bytes");
                mismatch.addSuppressed(cleanupFailure);
                throw mismatch;
            }
            throw new IllegalArgumentException(
                    "Stored Skill package digest does not match inspected bytes");
        }
        return new StoredSkillPackage(
                key.value(),
                stored.contentLength(),
                MEDIA_TYPE,
                stored.sha256());
    }

    @Override
    public StoredSkillPackageContent open(String objectKey) {
        var content = objects.open(new ObjectKey(objectKey));
        var stored = content.metadata();
        return new StoredSkillPackageContent(
                content.stream(),
                new StoredSkillPackage(
                        stored.key().value(),
                        stored.contentLength(),
                        stored.mediaType(),
                        stored.sha256()));
    }

    @Override
    public void delete(String objectKey) {
        objects.delete(new ObjectKey(objectKey));
    }
}
