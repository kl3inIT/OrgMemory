package com.orgmemory.core.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.retrieval.embedding")
public record KnowledgeEmbeddingProperties(
        String provider,
        String model,
        Integer dimensions) {

    public KnowledgeEmbeddingProperties {
        provider = provider == null || provider.isBlank()
                ? "openai"
                : provider.strip();
        model = model == null || model.isBlank()
                ? "text-embedding-3-large"
                : model.strip();
        dimensions = dimensions == null ? 1536 : dimensions;
        Assert.isTrue(
                dimensions > 0 && dimensions <= 16_000,
                "orgmemory.retrieval.embedding.dimensions must be between 1 and 16000");
    }
}
