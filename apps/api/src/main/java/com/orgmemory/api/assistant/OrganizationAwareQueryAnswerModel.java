package com.orgmemory.api.assistant;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.core.ai.AiRouteResolver;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.query.QueryAnswerModel;
import com.orgmemory.integrations.ai.gateway.SpringAiChatModelProvider;
import com.orgmemory.integrations.graphrag.springai.SpringAiQueryAnswerModel;
import java.util.Objects;

/** Request-time organization dispatch for direct LightRAG answer mode. */
final class OrganizationAwareQueryAnswerModel implements QueryAnswerModel {

    private final AiRouteResolver routes;
    private final SpringAiChatModelProvider models;

    OrganizationAwareQueryAnswerModel(
            AiRouteResolver routes,
            SpringAiChatModelProvider models) {
        this.routes = Objects.requireNonNull(routes, "routes");
        this.models = Objects.requireNonNull(models, "models");
    }

    @Override
    public ProcessingComponentRef component() {
        return new ProcessingComponentRef(
                "organization-aware-query-answer",
                "1");
    }

    @Override
    public Response answer(Request request) {
        Objects.requireNonNull(request, "request");
        var organizationId = Objects.requireNonNull(
                request.organizationId(),
                "organizationId");
        AiRoute route = routes.resolve(
                organizationId,
                AiWorkload.ASSISTANT_CHAT);
        return new SpringAiQueryAnswerModel(
                        route.modelId(),
                        models.resolve(
                                organizationId,
                                AiWorkload.ASSISTANT_CHAT,
                                route))
                .answer(request);
    }
}
