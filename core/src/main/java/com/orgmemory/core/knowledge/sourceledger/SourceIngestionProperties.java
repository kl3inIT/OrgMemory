package com.orgmemory.core.knowledge.sourceledger;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("orgmemory.ingestion")
public record SourceIngestionProperties(DataSize maximumRequestSize, Integer maximumAttempts) {

    public SourceIngestionProperties {
        maximumRequestSize = maximumRequestSize == null ? DataSize.ofMegabytes(25) : maximumRequestSize;
        maximumAttempts = maximumAttempts == null ? 5 : maximumAttempts;
        Assert.isTrue(maximumRequestSize.toBytes() > 0, "maximum request size must be positive");
        Assert.isTrue(maximumAttempts > 0, "maximum attempts must be positive");
    }
}
