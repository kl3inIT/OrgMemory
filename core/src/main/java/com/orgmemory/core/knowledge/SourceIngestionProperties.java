package com.orgmemory.core.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("orgmemory.ingestion")
public record SourceIngestionProperties(DataSize maximumUploadSize, Integer maximumAttempts) {

    public SourceIngestionProperties {
        maximumUploadSize = maximumUploadSize == null ? DataSize.ofMegabytes(25) : maximumUploadSize;
        maximumAttempts = maximumAttempts == null ? 5 : maximumAttempts;
        Assert.isTrue(maximumUploadSize.toBytes() > 0, "maximum upload size must be positive");
        Assert.isTrue(maximumAttempts > 0, "maximum attempts must be positive");
    }
}
