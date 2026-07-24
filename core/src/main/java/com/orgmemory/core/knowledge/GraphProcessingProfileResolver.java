package com.orgmemory.core.knowledge;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.graphrag.extraction.LightRagExtractionPrompt;
import com.orgmemory.graphrag.model.ExtractionProfile;
import com.orgmemory.graphrag.processing.GraphProcessingProfile;
import com.orgmemory.graphrag.processing.LightRagGraphProcessingProfiles;
import org.springframework.stereotype.Service;

/** Resolves the desired immutable profile before a graph job enters the queue. */
@Service
class GraphProcessingProfileResolver {

    private final AiRouteResolver routes;
    private final GraphProcessingProperties properties;

    GraphProcessingProfileResolver(
            AiRouteResolver routes,
            GraphProcessingProperties properties) {
        this.routes = routes;
        this.properties = properties;
    }

    GraphProcessingProfile current() {
        AiRoute route = routes.resolve(AiWorkload.GRAPH_EXTRACTION);
        return LightRagGraphProcessingProfiles.current(new ExtractionProfile(
                route.gatewayId(),
                route.modelId(),
                LightRagExtractionPrompt.VERSION,
                properties.maximumEntitiesPerChunk(),
                properties.maximumRelationsPerChunk(),
                properties.entityTypeGuidance(),
                properties.extractionExamples(),
                properties.maximumGleaningRounds(),
                properties.maximumGleaningInputTokens(),
                properties.maximumSectionContextTokens()));
    }
}
