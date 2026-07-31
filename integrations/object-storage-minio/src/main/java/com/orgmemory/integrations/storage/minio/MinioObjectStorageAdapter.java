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
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
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
        Path staged = null;
        try {
            staged = Files.createTempFile("orgmemory-object-", ".upload");
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            long copied;
            try (OutputStream output = new DigestOutputStream(
                    Files.newOutputStream(staged), sha256)) {
                copied = copyExactly(content, output, request.contentLength());
            }
            if (copied != request.contentLength()) {
                throw new IllegalArgumentException(
                        "object content length does not match the declared length");
            }
            String contentSha256 = HexFormat.of().formatHex(sha256.digest());
            Map<String, String> metadata = new LinkedHashMap<>(request.metadata());
            metadata.put("sha256", contentSha256);
            io.minio.ObjectWriteResponse response;
            try (InputStream stagedContent = Files.newInputStream(staged)) {
                response = client.putObject(PutObjectArgs.builder()
                        .bucket(properties.bucket())
                        .object(request.key().value())
                        .stream(stagedContent, copied, -1L)
                        .contentType(request.mediaType())
                        .userMetadata(Map.copyOf(metadata))
                        .build());
            }
            return new StoredObject(
                    request.key(),
                    copied,
                    request.mediaType(),
                    contentSha256,
                    response.etag(),
                    response.versionId());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure("write", request.key(), exception);
        } finally {
            deleteStagedFile(staged);
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

    private StoredObject stat(ObjectKey key) {
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

    private static long copyExactly(
            InputStream input,
            OutputStream output,
            long expectedLength) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            copied += read;
            if (copied > expectedLength) {
                throw new IllegalArgumentException(
                        "object content exceeds the declared length");
            }
            output.write(buffer, 0, read);
        }
        return copied;
    }

    private static void deleteStagedFile(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException cleanupFailure) {
            staged.toFile().deleteOnExit();
        }
    }
}
