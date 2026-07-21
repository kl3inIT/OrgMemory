package com.orgmemory.integrations.storage.minio;

import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectStorageException;
import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import com.orgmemory.core.knowledge.storage.StoredObject;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

final class MinioObjectStorageAdapter implements ObjectStoragePort {

    private final MinioClient client;
    private final MinioObjectStorageProperties properties;
    private final AtomicBoolean bucketReady = new AtomicBoolean();

    MinioObjectStorageAdapter(MinioClient client, MinioObjectStorageProperties properties) {
        this.client = client;
        this.properties = properties;
    }

    @Override
    public StoredObject put(ObjectWriteRequest request, InputStream content) {
        if (request.contentLength() > properties.maximumObjectSize().toBytes()) {
            throw new IllegalArgumentException("object exceeds the configured maximum size");
        }
        ensureBucket();
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            var digestingContent = new DigestInputStream(content, sha256);
            var response = client.putObject(PutObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(request.key().value())
                    .stream(digestingContent, request.contentLength(), -1L)
                    .contentType(request.mediaType())
                    .userMetadata(request.metadata())
                    .build());
            return new StoredObject(
                    request.key(),
                    request.contentLength(),
                    request.mediaType(),
                    HexFormat.of().formatHex(sha256.digest()),
                    response.etag(),
                    response.versionId());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (Exception exception) {
            throw failure("write", request.key(), exception);
        }
    }

    @Override
    public ObjectContent open(ObjectKey key) {
        try {
            StoredObject metadata = stat(key);
            InputStream stream = client.getObject(GetObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key.value())
                    .build());
            return new ObjectContent(stream, metadata);
        } catch (ObjectStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("open", key, exception);
        }
    }

    @Override
    public StoredObject stat(ObjectKey key) {
        try {
            StatObjectResponse response = client.statObject(StatObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key.value())
                    .build());
            var shaValues = response.userMetadata().get("sha256");
            return new StoredObject(
                    key,
                    response.size(),
                    response.contentType(),
                    shaValues == null ? null : shaValues.stream().findFirst().orElse(null),
                    response.etag(),
                    response.versionId());
        } catch (Exception exception) {
            throw failure("inspect", key, exception);
        }
    }

    @Override
    public void delete(ObjectKey key) {
        try {
            client.removeObject(RemoveObjectArgs.builder()
                    .bucket(properties.bucket())
                    .object(key.value())
                    .build());
        } catch (Exception exception) {
            throw failure("delete", key, exception);
        }
    }

    private void ensureBucket() {
        if (bucketReady.get()) {
            return;
        }
        synchronized (bucketReady) {
            if (bucketReady.get()) {
                return;
            }
            try {
                boolean exists = client.bucketExists(BucketExistsArgs.builder()
                        .bucket(properties.bucket())
                        .build());
                if (!exists) {
                    client.makeBucket(MakeBucketArgs.builder()
                            .bucket(properties.bucket())
                            .build());
                }
                bucketReady.set(true);
            } catch (Exception exception) {
                throw new ObjectStorageException("Could not prepare the evidence object bucket", exception);
            }
        }
    }

    private static ObjectStorageException failure(String operation, ObjectKey key, Exception cause) {
        return new ObjectStorageException(
                "Could not " + operation + " object " + key.value(), cause);
    }
}
