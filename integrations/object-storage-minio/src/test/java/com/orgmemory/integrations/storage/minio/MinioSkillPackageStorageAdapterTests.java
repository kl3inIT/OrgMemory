package com.orgmemory.integrations.storage.minio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.SkillPackageStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.StoredObject;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MinioSkillPackageStorageAdapterTests {

    @Test
    void writesAnOrganizationScopedKeyAndReturnsTheVerifiedDigest() {
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        UUID organizationId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        String digest = "a".repeat(64);
        ObjectKey key = new ObjectKey(
                "assets/skills/" + organizationId + "/" + packageId + ".zip");
        when(objects.put(any(), any())).thenReturn(
                new StoredObject(
                        key, 3, "application/zip", digest, "etag", "version"));
        MinioSkillPackageStorageAdapter adapter =
                new MinioSkillPackageStorageAdapter(objects);

        var stored = adapter.put(
                new SkillPackageStoragePort.SkillPackageWriteRequest(
                        organizationId,
                        packageId,
                        3,
                        digest,
                        Map.of("skill-name", "support-triage")),
                new ByteArrayInputStream(new byte[] {1, 2, 3}));

        assertEquals(key.value(), stored.objectKey());
        assertEquals(digest, stored.sha256());
    }

    @Test
    void deletesAStoredObjectWhoseDigestDoesNotMatchInspection() {
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        UUID organizationId = UUID.randomUUID();
        UUID packageId = UUID.randomUUID();
        ObjectKey key = new ObjectKey(
                "assets/skills/" + organizationId + "/" + packageId + ".zip");
        when(objects.put(any(), any())).thenReturn(
                new StoredObject(
                        key,
                        3,
                        "application/zip",
                        "b".repeat(64),
                        "etag",
                        "version"));
        MinioSkillPackageStorageAdapter adapter =
                new MinioSkillPackageStorageAdapter(objects);

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.put(
                        new SkillPackageStoragePort.SkillPackageWriteRequest(
                                organizationId,
                                packageId,
                                3,
                                "a".repeat(64),
                                Map.of()),
                        new ByteArrayInputStream(new byte[] {1, 2, 3})));

        verify(objects).delete(key);
    }

    @Test
    void opensStoredPackageContentWithoutLeakingStorageTypesToCore() {
        ObjectStoragePort objects = mock(ObjectStoragePort.class);
        ObjectKey key = new ObjectKey("assets/skills/org/package.zip");
        String digest = "a".repeat(64);
        ByteArrayInputStream stream =
                new ByteArrayInputStream(new byte[] {1, 2, 3});
        when(objects.open(key)).thenReturn(new ObjectContent(
                stream,
                new StoredObject(
                        key,
                        3,
                        "application/zip",
                        digest,
                        "etag",
                        "version")));
        MinioSkillPackageStorageAdapter adapter =
                new MinioSkillPackageStorageAdapter(objects);

        var content = adapter.open(key.value());

        assertEquals(key.value(), content.metadata().objectKey());
        assertEquals(digest, content.metadata().sha256());
        assertEquals(stream, content.content());
    }
}
