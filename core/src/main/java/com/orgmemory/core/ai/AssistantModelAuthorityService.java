package com.orgmemory.core.ai;

import com.orgmemory.core.shared.error.BusinessConflictException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves browser-safe Assistant choices into server-only exact route authority. */
@Service
public class AssistantModelAuthorityService {

    private final AiRouteResolver routes;
    private final AiGatewayAdministrationService administration;
    private final AiAssistantModelActivationRepository activations;

    AssistantModelAuthorityService(
            AiRouteResolver routes,
            AiGatewayAdministrationService administration,
            AiAssistantModelActivationRepository activations) {
        this.routes = routes;
        this.administration = administration;
        this.activations = activations;
    }

    @Transactional(readOnly = true)
    public List<AssistantModelChoice> choices(UUID organizationId) {
        AiRoute route = routes.reference(organizationId, AiWorkload.ASSISTANT_CHAT);
        Optional<AiRouteOverrideView> override = administration.route(
                organizationId,
                AiWorkload.ASSISTANT_CHAT);
        String gatewayLabel = route.gatewayId();
        String provider = provider(route.gatewayId());
        if (override.isPresent()) {
            AiGatewayProfileView profile = administration.require(
                    organizationId,
                    override.orElseThrow().gatewayProfileId());
            gatewayLabel = profile.displayName();
            provider = provider(profile.preset());
        }
        String effectiveGatewayLabel = gatewayLabel;
        String effectiveProvider = provider;

        ArrayList<AssistantModelChoice> result = new ArrayList<>();
        result.add(new AssistantModelChoice(
                null,
                effectiveGatewayLabel,
                effectiveProvider,
                route.modelId(),
                route.modelId(),
                true));
        if (override.isEmpty() || !supportsCatalog(route.openAiReasoningEffort())) {
            return List.copyOf(result);
        }
        administration.assistantModels(
                        organizationId,
                        override.orElseThrow().gatewayProfileId())
                .stream()
                .filter(model -> !model.modelId().equals(route.modelId()))
                .map(model -> new AssistantModelChoice(
                        model.id(),
                        effectiveGatewayLabel,
                        effectiveProvider,
                        model.modelId(),
                        model.displayName(),
                        false))
                .forEach(result::add);
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public AssistantModelRouteAuthority authorize(
            UUID organizationId,
            UUID activationId) {
        AiRoute route = routes.reference(organizationId, AiWorkload.ASSISTANT_CHAT);
        Optional<AiRouteOverrideView> override = administration.route(
                organizationId,
                AiWorkload.ASSISTANT_CHAT);
        if (activationId == null) {
            return new DefaultAssistantModelRouteAuthority(
                    organizationId,
                    route,
                    override.map(AiRouteOverrideView::id).orElse(null),
                    override.map(AiRouteOverrideView::version).orElse(0L));
        }
        AiRouteOverrideView selectedRoute = override.orElseThrow(
                AssistantModelAuthorityService::unavailable);
        if (!supportsCatalog(selectedRoute.openAiReasoningEffort())) {
            throw unavailable();
        }
        AiAssistantModelActivation activation = activations
                .findByIdAndOrganizationIdAndEnabledTrue(
                        activationId,
                        organizationId)
                .orElseThrow(AssistantModelAuthorityService::unavailable);
        if (!activation.gatewayProfileId().equals(selectedRoute.gatewayProfileId())) {
            throw unavailable();
        }
        return new CatalogAssistantModelRouteAuthority(
                organizationId,
                activation.getId(),
                activation.gatewayProfileId(),
                selectedRoute.id(),
                selectedRoute.version());
    }

    @Transactional(readOnly = true)
    public AiRoute revalidate(AssistantModelRouteAuthority authority) {
        if (authority instanceof DefaultAssistantModelRouteAuthority selected) {
            return revalidateDefault(selected);
        }
        if (authority instanceof CatalogAssistantModelRouteAuthority selected) {
            return revalidateCatalog(selected);
        }
        throw unavailable();
    }

    public AssistantModelSelectionRef selectionRef(
            AssistantModelRouteAuthority authority) {
        if (authority instanceof CatalogAssistantModelRouteAuthority selected) {
            return new AssistantModelSelectionRef(
                    selected.activationId(),
                    selected.routeOverrideId(),
                    selected.routeOverrideVersion());
        }
        return null;
    }

    @Transactional(readOnly = true)
    public UUID resolveSelectedActivation(
            UUID organizationId,
            AssistantModelSelectionRef selection) {
        if (selection == null) {
            return null;
        }
        try {
            AssistantModelRouteAuthority authority = authorize(
                    organizationId,
                    selection.activationId());
            if (authority instanceof CatalogAssistantModelRouteAuthority selected
                    && selected.routeOverrideId().equals(selection.routeOverrideId())
                    && selected.routeOverrideVersion() == selection.routeOverrideVersion()) {
                return selected.activationId();
            }
        } catch (BusinessConflictException ignored) {
            // Stale selections intentionally collapse to the current default.
        }
        return null;
    }

    private AiRoute revalidateDefault(DefaultAssistantModelRouteAuthority selected) {
        AiRoute current = routes.reference(
                selected.organizationId(),
                AiWorkload.ASSISTANT_CHAT);
        Optional<AiRouteOverrideView> override = administration.route(
                selected.organizationId(),
                AiWorkload.ASSISTANT_CHAT);
        if (selected.routeOverrideId() == null) {
            if (override.isPresent() || !current.equals(selected.route())) {
                throw unavailable();
            }
            return current;
        }
        AiRouteOverrideView active = override.orElseThrow(
                AssistantModelAuthorityService::unavailable);
        if (!active.id().equals(selected.routeOverrideId())
                || active.version() != selected.routeOverrideVersion()
                || !active.route().equals(selected.route())) {
            throw unavailable();
        }
        return current;
    }

    private AiRoute revalidateCatalog(CatalogAssistantModelRouteAuthority selected) {
        AiRouteOverrideView active = administration.route(
                        selected.organizationId(),
                        AiWorkload.ASSISTANT_CHAT)
                .orElseThrow(AssistantModelAuthorityService::unavailable);
        if (!active.id().equals(selected.routeOverrideId())
                || active.version() != selected.routeOverrideVersion()
                || !active.gatewayProfileId().equals(selected.gatewayProfileId())
                || !supportsCatalog(active.openAiReasoningEffort())) {
            throw unavailable();
        }
        AiAssistantModelActivation activation = activations
                .findByIdAndOrganizationIdAndEnabledTrue(
                        selected.activationId(),
                        selected.organizationId())
                .orElseThrow(AssistantModelAuthorityService::unavailable);
        if (!activation.gatewayProfileId().equals(selected.gatewayProfileId())) {
            throw unavailable();
        }
        AiGatewayProfileView profile = administration.require(
                selected.organizationId(),
                selected.gatewayProfileId());
        return new AiRoute(
                profile.gatewayKey(),
                activation.modelId(),
                active.openAiReasoningEffort());
    }

    private static boolean supportsCatalog(OpenAiReasoningEffort reasoning) {
        return reasoning == null || reasoning == OpenAiReasoningEffort.NONE;
    }

    private static BusinessConflictException unavailable() {
        return new BusinessConflictException(
                "assistant.model-selection-unavailable",
                "The selected Assistant model is no longer available");
    }

    private static String provider(AiGatewayPreset preset) {
        return switch (preset) {
            case OPENAI -> "openai";
            case ANTHROPIC -> "anthropic";
            case OPENROUTER -> "openrouter";
            case OLLAMA -> "ollama";
            case NINE_ROUTER, LITELLM, OPENAI_COMPATIBLE -> "custom";
        };
    }

    private static String provider(String gatewayId) {
        String normalized = gatewayId.toLowerCase(Locale.ROOT);
        if (normalized.contains("anthropic") || normalized.contains("claude")) {
            return "anthropic";
        }
        if (normalized.contains("openrouter")) {
            return "openrouter";
        }
        if (normalized.contains("ollama")) {
            return "ollama";
        }
        if (normalized.contains("openai")) {
            return "openai";
        }
        return "custom";
    }
}
