package com.orgmemory.api.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.retrieval.embedding")
public record QueryEmbeddingProperties(
        String provider,
        String model,
        Integer dimensions) {

    public QueryEmbeddingProperties {
        provider = provider == null || provider.isBlank() ? "openai" : provider.strip();
        model = model == null || model.isBlank() ? "text-embedding-3-large" : model.strip();
        dimensions = dimensions == null ? 1536 : dimensions;
        Assert.isTrue(dimensions > 0 && dimensions <= 16000,
                "orgmemory.retrieval.embedding.dimensions must be between 1 and 16000");
    }
}
