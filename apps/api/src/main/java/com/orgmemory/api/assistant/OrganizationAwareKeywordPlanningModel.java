package com.orgmemory.api.assistant;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.graphrag.cache.CanonicalCacheKeyHasher;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.query.KeywordPlan;
import com.orgmemory.graphrag.query.KeywordPlanningModel;
import com.orgmemory.integrations.ai.gateway.SpringAiChatModelProvider;
import com.orgmemory.integrations.graphrag.springai.SpringAiKeywordPlanningModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Resolves one immutable organization route snapshot per keyword-plan attempt. */
final class OrganizationAwareKeywordPlanningModel
        implements KeywordPlanningModel {

    private final AiRouteResolver routes;
    private final SpringAiChatModelProvider models;

    OrganizationAwareKeywordPlanningModel(
            AiRouteResolver routes,
            SpringAiChatModelProvider models) {
        this.routes = Objects.requireNonNull(routes, "routes");
        this.models = Objects.requireNonNull(models, "models");
    }

    @Override
    public Invocation resolve(UUID organizationId) {
        UUID requiredOrganization = Objects.requireNonNull(
                organizationId,
                "organizationId");
        AiRoute route = routes.resolve(
                requiredOrganization,
                AiWorkload.KEYWORD_PLANNING);
        ProcessingComponentRef component = new ProcessingComponentRef(
                route.modelId(),
                "lightrag-keyword-v1");
        return new Invocation(
                component,
                fingerprint(route),
                prompt -> new SpringAiKeywordPlanningModel(
                                route.modelId(),
                                models.resolve(
                                        requiredOrganization,
                                        AiWorkload.KEYWORD_PLANNING,
                                        route))
                        .complete(prompt));
    }

    @Override
    public ProcessingComponentRef component() {
        return new ProcessingComponentRef(
                "organization-aware-keyword-planner",
                "1");
    }

    @Override
    public KeywordPlan complete(String prompt) {
        throw new IllegalStateException(
                "Keyword planning requires an organization-scoped route");
    }

    private static String fingerprint(AiRoute route) {
        Map<String, String> coordinates = new LinkedHashMap<>();
        coordinates.put("gatewayId", route.gatewayId());
        coordinates.put("modelId", route.modelId());
        coordinates.put(
                "openAiReasoningEffort",
                route.openAiReasoningEffort() == null
                        ? ""
                        : route.openAiReasoningEffort().wireValue());
        return CanonicalCacheKeyHasher.sha256(
                "orgmemory.ai.keyword-route.v2",
                coordinates);
    }
}
