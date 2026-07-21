package com.orgmemory.integrations.storage.minio;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("orgmemory.storage.object")
public record MinioObjectStorageProperties(
        URI endpoint,
        String accessKey,
        String secretKey,
        String bucket,
        DataSize maximumObjectSize) {

    public MinioObjectStorageProperties {
        Assert.notNull(endpoint, "orgmemory.storage.object.endpoint is required");
        Assert.hasText(accessKey, "orgmemory.storage.object.access-key is required");
        Assert.hasText(secretKey, "orgmemory.storage.object.secret-key is required");
        Assert.hasText(bucket, "orgmemory.storage.object.bucket is required");
        maximumObjectSize = maximumObjectSize == null ? DataSize.ofMegabytes(25) : maximumObjectSize;
        Assert.isTrue(maximumObjectSize.toBytes() > 0, "maximum object size must be positive");
    }
}
