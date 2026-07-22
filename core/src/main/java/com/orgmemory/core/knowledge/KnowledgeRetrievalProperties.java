package com.orgmemory.core.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.retrieval")
public record KnowledgeRetrievalProperties(
        Integer maximumResults,
        Integer candidateMultiplier,
        Integer maximumAuthorizedObjects,
        Integer maximumQueryLength) {

    public KnowledgeRetrievalProperties {
        maximumResults = maximumResults == null ? 20 : maximumResults;
        candidateMultiplier = candidateMultiplier == null ? 5 : candidateMultiplier;
        maximumAuthorizedObjects = maximumAuthorizedObjects == null ? 5_000 : maximumAuthorizedObjects;
        maximumQueryLength = maximumQueryLength == null ? 1_000 : maximumQueryLength;
        Assert.isTrue(maximumResults > 0 && maximumResults <= 100,
                "maximum-results must be between 1 and 100");
        Assert.isTrue(candidateMultiplier > 0 && candidateMultiplier <= 20,
                "candidate-multiplier must be between 1 and 20");
        Assert.isTrue(maximumAuthorizedObjects > 0,
                "maximum-authorized-objects must be positive");
        Assert.isTrue(maximumQueryLength > 0, "maximum-query-length must be positive");
    }
}
