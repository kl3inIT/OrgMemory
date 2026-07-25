package com.orgmemory.integrations.storage.minio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.ObjectWriteRequest;
import io.minio.Http;
import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import io.minio.StatObjectResponse;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Map;
import okhttp3.Headers;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class MinioObjectStorageAdapterTests {

    @Test
    void persistsTheCanonicalSha256AsObjectMetadata() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        byte[] payload = "permission-aware evidence".getBytes(StandardCharsets.UTF_8);
        String expectedSha = HexFormat.of().formatHex(
                java.security.MessageDigest.getInstance("SHA-256").digest(payload));
        when(client.putObject(any())).thenAnswer(invocation -> {
            PutObjectArgs args = invocation.getArgument(0);
            assertEquals(payload.length, args.objectSize());
            assertEquals(
                    java.util.Set.of(expectedSha),
                    args.userMetadata().get("x-amz-meta-sha256"));
            assertEquals(
                    java.util.Set.of("unit-test"),
                    args.userMetadata().get("x-amz-meta-source"));
            assertEquals(
                    "permission-aware evidence",
                    new String(args.stream().readAllBytes(), StandardCharsets.UTF_8));
            return new ObjectWriteResponse(
                    new Headers.Builder().build(),
                    "orgmemory",
                    "local",
                    "evidence/test.txt",
                    "etag-1",
                    "version-1");
        });
        StatObjectResponse stat = mock(StatObjectResponse.class);
        when(stat.size()).thenReturn((long) payload.length);
        when(stat.contentType()).thenReturn("text/plain");
        when(stat.userMetadata()).thenReturn(new Http.Headers(Map.of("sha256", expectedSha)));
        when(stat.etag()).thenReturn("etag-1");
        when(stat.versionId()).thenReturn("version-1");
        when(client.statObject(any())).thenReturn(stat);
        MinioObjectStorageAdapter adapter = adapter(client);
        ObjectKey key = new ObjectKey("evidence/test.txt");

        var stored = adapter.put(
                new ObjectWriteRequest(
                        key,
                        payload.length,
                        "text/plain",
                        Map.of("source", "unit-test")),
                new ByteArrayInputStream(payload));

        assertEquals(expectedSha, stored.sha256());
        assertEquals(expectedSha, adapter.stat(key).sha256());
    }

    @Test
    void rejectsAStreamThatDoesNotMatchItsDeclaredLengthBeforeUpload() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        MinioObjectStorageAdapter adapter = adapter(client);

        assertThrows(
                IllegalArgumentException.class,
                () -> adapter.put(
                        new ObjectWriteRequest(
                                new ObjectKey("evidence/mismatch.txt"),
                                2,
                                "text/plain",
                                Map.of()),
                        new ByteArrayInputStream(new byte[] {1, 2, 3})));

        verify(client, never()).putObject(any(PutObjectArgs.class));
    }

    private static MinioObjectStorageAdapter adapter(MinioClient client) {
        return new MinioObjectStorageAdapter(
                client,
                new MinioObjectStorageProperties(
                        URI.create("http://localhost:9000"),
                        "access",
                        "secret",
                        "orgmemory",
                        DataSize.ofMegabytes(25)));
    }
}
