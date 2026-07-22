package com.orgmemory.worker.connector;

import com.orgmemory.core.knowledge.ConnectorEmbeddingResult;
import com.orgmemory.core.knowledge.ConnectorTextEmbedder;
import com.orgmemory.core.knowledge.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.EmbeddingProfileRef;
import com.orgmemory.core.knowledge.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.EmbeddingProfileSpec;
import com.orgmemory.worker.ingestion.SourceProcessingProperties;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.TokenCountBatchingStrategy;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * The runtime {@link ConnectorTextEmbedder}: it resolves the same immutable embedding profile
 * the upload pipeline uses and embeds connector chunk texts with the configured model. The
 * connector owns chunking and persistence; this adapter owns only the model call, so tests can
 * substitute a deterministic embedder without touching the connector use case.
 */
@Component
class ModelConnectorTextEmbedder implements ConnectorTextEmbedder {

    private final EmbeddingProfileRegistry profiles;
    private final ObjectProvider<EmbeddingModel> models;
    private final SourceProcessingProperties properties;

    ModelConnectorTextEmbedder(
            EmbeddingProfileRegistry profiles,
            ObjectProvider<EmbeddingModel> models,
            SourceProcessingProperties properties) {
        this.profiles = profiles;
        this.models = models;
        this.properties = properties;
    }

    @Override
    public ConnectorEmbeddingResult embed(UUID organizationId, List<String> chunkTexts) {
        if (chunkTexts.isEmpty()) {
            throw new IllegalArgumentException("connector embedding requires at least one chunk");
        }
        EmbeddingModel model = models.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("Embedding is not configured for the connector");
        }
        EmbeddingProfileRef profile = profiles.resolve(
                organizationId,
                new EmbeddingProfileSpec(
                        properties.embeddingProvider(),
                        properties.embeddingModel(),
                        properties.embeddingDimensions(),
                        EmbeddingDistanceMetric.COSINE));
        List<Document> documents = chunkTexts.stream().map(Document::new).toList();
        List<float[]> vectors = model.embed(documents, null, new TokenCountBatchingStrategy());
        if (vectors.size() != chunkTexts.size()) {
            throw new IllegalStateException("connector embedding response count did not match the chunk count");
        }
        return new ConnectorEmbeddingResult(profile, vectors);
    }
}
