package com.orgmemory.api.knowledge;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.EmbeddingProfileSpec;
import com.orgmemory.core.knowledge.QueryEmbedding;
import com.orgmemory.core.knowledge.QueryEmbeddingPort;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
final class SpringAiQueryEmbeddingAdapter implements QueryEmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiQueryEmbeddingAdapter.class);

    private final ObjectProvider<EmbeddingModel> models;
    private final EmbeddingProfileRegistry profiles;
    private final QueryEmbeddingProperties properties;
    private final AiRouteResolver routes;

    SpringAiQueryEmbeddingAdapter(
            ObjectProvider<EmbeddingModel> models,
            EmbeddingProfileRegistry profiles,
            QueryEmbeddingProperties properties,
            AiRouteResolver routes) {
        this.models = models;
        this.profiles = profiles;
        this.properties = properties;
        this.routes = routes;
    }

    @Override
    public Optional<QueryEmbedding> embed(UUID organizationId, String query) {
        try {
            var route = routes.resolve(AiWorkload.QUERY_EMBEDDING);
            if (!route.modelId().equals(properties.model())) {
                log.error(
                        "Skipping semantic retrieval because gateway route model {} differs from embedding profile model {}",
                        route.modelId(),
                        properties.model());
                return Optional.empty();
            }
        } catch (AiGatewayUnavailableException exception) {
            log.warn("Semantic retrieval gateway is unavailable; continuing with lexical retrieval");
            return Optional.empty();
        }
        EmbeddingModel model = models.getIfAvailable();
        if (model == null) {
            return Optional.empty();
        }
        var spec = new EmbeddingProfileSpec(
                properties.provider(),
                properties.model(),
                properties.dimensions(),
                EmbeddingDistanceMetric.COSINE);
        var profile = profiles.find(organizationId, spec);
        if (profile.isEmpty()) {
            log.info("Skipping semantic retrieval because the configured embedding profile has no indexed data");
            return Optional.empty();
        }
        try {
            float[] vector = model.embed(query);
            if (vector.length != profile.get().dimensions()) {
                log.error(
                        "Skipping semantic retrieval because query embedding dimensions {} differ from profile {}",
                        vector.length,
                        profile.get().dimensions());
                return Optional.empty();
            }
            return Optional.of(new QueryEmbedding(profile.get().id(), vector.length, vector));
        } catch (RuntimeException exception) {
            log.warn("Semantic retrieval is unavailable; continuing with lexical retrieval", exception);
            return Optional.empty();
        }
    }
}
