package com.orgmemory.api.knowledge;

import com.orgmemory.core.ai.AiGatewayUnavailableException;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.retrieval.EmbeddingDistanceMetric;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileRegistry;
import com.orgmemory.core.knowledge.retrieval.EmbeddingProfileSpec;
import com.orgmemory.core.knowledge.retrieval.KnowledgeEmbeddingProperties;
import com.orgmemory.core.knowledge.retrieval.QueryEmbedding;
import com.orgmemory.core.knowledge.retrieval.QueryEmbeddingPort;
import com.orgmemory.graphrag.query.CachingQueryEmbeddingService;
import com.orgmemory.graphrag.storage.ProjectionNamespace;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
final class SpringAiQueryEmbeddingAdapter implements QueryEmbeddingPort {

    private static final Logger log = LoggerFactory.getLogger(SpringAiQueryEmbeddingAdapter.class);

    private final CachingQueryEmbeddingService embeddings;
    private final EmbeddingProfileRegistry profiles;
    private final KnowledgeEmbeddingProperties properties;
    private final AiRouteResolver routes;

    SpringAiQueryEmbeddingAdapter(
            CachingQueryEmbeddingService embeddings,
            EmbeddingProfileRegistry profiles,
            KnowledgeEmbeddingProperties properties,
            AiRouteResolver routes) {
        this.embeddings = embeddings;
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
            var vector = embeddings.embedAll(
                            new ProjectionNamespace(
                                    organizationId,
                                    "default",
                                    "canonical-query"),
                            profile.get().id(),
                            profile.get().dimensions(),
                            List.of(query))
                    .getFirst();
            return Optional.of(new QueryEmbedding(
                    profile.get().id(),
                    vector.dimensions(),
                    vector.copyValues()));
        } catch (RuntimeException exception) {
            log.warn("Semantic retrieval is unavailable; continuing with lexical retrieval", exception);
            return Optional.empty();
        }
    }
}
