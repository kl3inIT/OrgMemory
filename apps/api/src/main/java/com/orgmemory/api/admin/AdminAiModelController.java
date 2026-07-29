package com.orgmemory.api.admin;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.core.ai.AiGatewayAdministrationService;
import com.orgmemory.core.ai.AiGatewayCategory;
import com.orgmemory.core.ai.AiGatewayPreset;
import com.orgmemory.core.ai.AiGatewayProfileView;
import com.orgmemory.core.ai.AiGatewayProtocol;
import com.orgmemory.core.ai.AiRouteOverrideView;
import com.orgmemory.core.ai.AiWorkload;
import com.orgmemory.core.knowledge.KnowledgeEmbeddingProperties;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.secret.SecretValue;
import com.orgmemory.integrations.ai.gateway.AiGatewayProperties;
import com.orgmemory.integrations.ai.gateway.AiModelCatalogProbe;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/ai")
class AdminAiModelController {

    private static final List<ProviderPresetResponse> PROVIDERS = List.of(
            new ProviderPresetResponse(
                    AiGatewayPreset.OPENAI,
                    "OpenAI",
                    "OpenAI",
                    AiGatewayCategory.DIRECT_PROVIDER,
                    AiGatewayProtocol.OPENAI_COMPATIBLE,
                    "https://api.openai.com/v1",
                    false),
            new ProviderPresetResponse(
                    AiGatewayPreset.ANTHROPIC,
                    "Claude",
                    "Anthropic",
                    AiGatewayCategory.DIRECT_PROVIDER,
                    AiGatewayProtocol.ANTHROPIC_MESSAGES,
                    "https://api.anthropic.com",
                    false),
            new ProviderPresetResponse(
                    AiGatewayPreset.NINE_ROUTER,
                    "9Router",
                    "9Router",
                    AiGatewayCategory.GATEWAY_ROUTER,
                    AiGatewayProtocol.OPENAI_COMPATIBLE,
                    "http://localhost:20128/v1",
                    true),
            new ProviderPresetResponse(
                    AiGatewayPreset.OPENROUTER,
                    "OpenRouter",
                    "OpenRouter",
                    AiGatewayCategory.GATEWAY_ROUTER,
                    AiGatewayProtocol.OPENAI_COMPATIBLE,
                    "https://openrouter.ai/api/v1",
                    false),
            new ProviderPresetResponse(
                    AiGatewayPreset.LITELLM,
                    "LiteLLM Proxy",
                    "LiteLLM",
                    AiGatewayCategory.GATEWAY_ROUTER,
                    AiGatewayProtocol.OPENAI_COMPATIBLE,
                    "",
                    true),
            new ProviderPresetResponse(
                    AiGatewayPreset.OLLAMA,
                    "Ollama",
                    "Ollama",
                    AiGatewayCategory.SELF_HOSTED_CUSTOM,
                    AiGatewayProtocol.OPENAI_COMPATIBLE,
                    "http://localhost:11434/v1",
                    true),
            new ProviderPresetResponse(
                    AiGatewayPreset.OPENAI_COMPATIBLE,
                    "OpenAI-Compatible",
                    "Custom",
                    AiGatewayCategory.SELF_HOSTED_CUSTOM,
                    AiGatewayProtocol.OPENAI_COMPATIBLE,
                    "",
                    true));

    private final AdminAccessGuard guard;
    private final AiGatewayAdministrationService gateways;
    private final AiModelCatalogProbe probes;
    private final AiGatewayProperties deployment;
    private final KnowledgeEmbeddingProperties embedding;

    AdminAiModelController(
            AdminAccessGuard guard,
            AiGatewayAdministrationService gateways,
            AiModelCatalogProbe probes,
            AiGatewayProperties deployment,
            KnowledgeEmbeddingProperties embedding) {
        this.guard = guard;
        this.gateways = gateways;
        this.probes = probes;
        this.deployment = deployment;
        this.embedding = embedding;
    }

    @GetMapping("/providers")
    @Operation(
            operationId = "listAdminAiProviderPresets",
            summary = "List provider presets implemented by this deployment")
    List<ProviderPresetResponse> providers(Authentication authentication) {
        guard.requireAiAdministrator(authentication);
        return PROVIDERS;
    }

    @GetMapping("/gateways")
    @Operation(
            operationId = "listAdminAiGateways",
            summary = "List organization AI gateway profiles")
    List<GatewayResponse> gateways(Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        return gateways.list(actor.organizationId()).stream()
                .map(GatewayResponse::from)
                .toList();
    }

    @PostMapping("/gateways")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "createAdminAiGateway",
            summary = "Connect an organization AI gateway")
    GatewayResponse create(
            @RequestBody CreateGatewayRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        require(request, "AI gateway settings are required");
        AiGatewayProfileView created = gateways.create(
                actor.organizationId(),
                request.gatewayKey(),
                request.displayName(),
                request.preset(),
                request.category(),
                request.protocol(),
                request.baseUrl(),
                request.requestTimeoutSeconds(),
                secret(request.credential()),
                actor.userId());
        return GatewayResponse.from(created);
    }

    @PutMapping("/gateways/{profileId}")
    @Operation(
            operationId = "updateAdminAiGateway",
            summary = "Update an organization AI gateway")
    GatewayResponse update(
            @PathVariable UUID profileId,
            @RequestBody UpdateGatewayRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        require(request, "AI gateway settings are required");
        return GatewayResponse.from(gateways.update(
                actor.organizationId(),
                profileId,
                request.displayName(),
                request.baseUrl(),
                request.requestTimeoutSeconds(),
                optionalSecret(request.credential()),
                actor.userId()));
    }

    @DeleteMapping("/gateways/{profileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "disableAdminAiGateway",
            summary = "Disable an unused organization AI gateway")
    void disable(
            @PathVariable UUID profileId,
            Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        gateways.disable(
                actor.organizationId(),
                profileId,
                actor.userId());
    }

    @PutMapping("/gateways/{profileId}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "setAdminAiGatewayCredential",
            summary = "Set or rotate an organization AI gateway credential")
    void setCredential(
            @PathVariable UUID profileId,
            @RequestBody GatewayCredentialRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        gateways.setCredential(
                actor.organizationId(),
                profileId,
                secret(request == null ? null : request.credential()),
                actor.userId());
    }

    @PostMapping("/gateways/test")
    @Operation(
            operationId = "testAdminAiGateway",
            summary = "Test an AI gateway credential without storing it")
    ProbeResponse test(
            @RequestBody TestGatewayRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        require(request, "AI gateway test settings are required");
        ProbeResponse response = ProbeResponse.from(probes.transientProbe(
                request.preset(),
                request.protocol(),
                request.baseUrl(),
                secret(request.credential()),
                Duration.ofSeconds(request.requestTimeoutSeconds() == null
                        ? 60
                        : request.requestTimeoutSeconds())));
        gateways.recordProbe(
                actor.organizationId(),
                actor.userId(),
                request.preset() == null
                        ? "TRANSIENT"
                        : request.preset().name(),
                response.authenticated(),
                response.errorCode());
        return response;
    }

    @PostMapping("/gateways/{profileId}/test")
    @Operation(
            operationId = "testStoredAdminAiGateway",
            summary = "Test a stored organization AI gateway")
    ProbeResponse testStored(
            @PathVariable UUID profileId,
            Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        AiGatewayProfileView profile =
                gateways.require(actor.organizationId(), profileId);
        ProbeResponse response = gateways.connection(
                        actor.organizationId(),
                        profile.gatewayKey())
                .map(connection -> ProbeResponse.from(
                        probes.storedProbe(profile.preset(), connection)))
                .orElseGet(() -> ProbeResponse.failed(
                        "credential_missing"));
        gateways.recordProbe(
                actor.organizationId(),
                actor.userId(),
                profile.id().toString(),
                response.authenticated(),
                response.errorCode());
        return response;
    }

    @GetMapping("/routes")
    @Operation(
            operationId = "listAdminAiRoutes",
            summary = "List effective organization AI routes")
    List<RouteResponse> routes(Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        Map<AiWorkload, AiRouteOverrideView> overrides =
                new EnumMap<>(AiWorkload.class);
        gateways.routes(actor.organizationId())
                .forEach(route -> overrides.put(route.workload(), route));
        return List.of(
                route(AiWorkload.ASSISTANT_CHAT, overrides),
                route(AiWorkload.PROMPT_EXECUTION, overrides),
                route(AiWorkload.KEYWORD_PLANNING, overrides),
                route(AiWorkload.GRAPH_EXTRACTION, overrides),
                route(AiWorkload.QUERY_EMBEDDING, overrides),
                route(AiWorkload.DOCUMENT_EMBEDDING, overrides));
    }

    @PutMapping("/routes/{workload}")
    @Operation(
            operationId = "setAdminAiRoute",
            summary = "Set an editable organization AI route")
    RouteResponse setRoute(
            @PathVariable AiWorkload workload,
            @RequestBody SetRouteRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        require(request, "AI route settings are required");
        AiRouteOverrideView route = gateways.setRoute(
                actor.organizationId(),
                workload,
                request.gatewayProfileId(),
                request.modelId(),
                actor.userId());
        return RouteResponse.override(route);
    }

    @DeleteMapping("/routes/{workload}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "clearAdminAiRoute",
            summary = "Restore the deployment default for an editable AI route")
    void clearRoute(
            @PathVariable AiWorkload workload,
            Authentication authentication) {
        CurrentActor actor = guard.requireAiAdministrator(authentication);
        gateways.clearRoute(
                actor.organizationId(),
                workload,
                actor.userId());
    }

    @GetMapping("/index-settings")
    @Operation(
            operationId = "getAdminAiIndexSettings",
            summary = "Read immutable deployment-managed embedding settings")
    IndexSettingsResponse indexSettings(Authentication authentication) {
        guard.requireAiAdministrator(authentication);
        return new IndexSettingsResponse(
                embedding.provider(),
                embedding.model(),
                embedding.dimensions(),
                "COSINE",
                "DEPLOYMENT_MANAGED",
                false,
                "Changing embedding geometry requires a new profile and reindex generation.");
    }

    private RouteResponse route(
            AiWorkload workload,
            Map<AiWorkload, AiRouteOverrideView> overrides) {
        AiRouteOverrideView override = overrides.get(workload);
        return override == null
                ? RouteResponse.deployment(
                        workload,
                        deployment.route(workload))
                : RouteResponse.override(override);
    }

    private static SecretValue secret(String credential) {
        try {
            return SecretValue.of(credential);
        } catch (IllegalArgumentException invalid) {
            throw new ApiRequestException(
                    "An AI gateway credential is required",
                    invalid);
        }
    }

    private static SecretValue optionalSecret(String credential) {
        return credential == null || credential.isBlank()
                ? null
                : secret(credential);
    }

    private static void require(Object request, String message) {
        if (request == null) {
            throw new ApiRequestException(message);
        }
    }

    record ProviderPresetResponse(
            AiGatewayPreset preset,
            String displayName,
            String vendorName,
            AiGatewayCategory category,
            AiGatewayProtocol protocol,
            String defaultBaseUrl,
            boolean baseUrlEditable) {
    }

    record GatewayResponse(
            UUID id,
            String gatewayKey,
            String displayName,
            AiGatewayPreset preset,
            AiGatewayCategory category,
            AiGatewayProtocol protocol,
            String baseUrl,
            int requestTimeoutSeconds,
            boolean enabled,
            long version,
            boolean credentialSet,
            UUID credentialSetByUserId,
            java.time.Instant credentialSetAt) {

        static GatewayResponse from(AiGatewayProfileView profile) {
            return new GatewayResponse(
                    profile.id(),
                    profile.gatewayKey(),
                    profile.displayName(),
                    profile.preset(),
                    profile.category(),
                    profile.protocol(),
                    profile.baseUrl(),
                    profile.requestTimeoutSeconds(),
                    profile.enabled(),
                    profile.version(),
                    profile.credentialSet(),
                    profile.credentialSetByUserId(),
                    profile.credentialSetAt());
        }
    }

    record CreateGatewayRequest(
            String gatewayKey,
            String displayName,
            AiGatewayPreset preset,
            AiGatewayCategory category,
            AiGatewayProtocol protocol,
            String baseUrl,
            Integer requestTimeoutSeconds,
            String credential) {

        @Override
        public String toString() {
            return "CreateGatewayRequest[gatewayKey=%s, displayName=%s, preset=%s, category=%s, protocol=%s, baseUrl=%s, requestTimeoutSeconds=%s, credential=<redacted>]"
                    .formatted(
                            gatewayKey,
                            displayName,
                            preset,
                            category,
                            protocol,
                            baseUrl,
                            requestTimeoutSeconds);
        }
    }

    record UpdateGatewayRequest(
            String displayName,
            String baseUrl,
            Integer requestTimeoutSeconds,
            String credential) {

        @Override
        public String toString() {
            return "UpdateGatewayRequest[displayName=%s, baseUrl=%s, requestTimeoutSeconds=%s, credential=<redacted>]"
                    .formatted(
                            displayName,
                            baseUrl,
                            requestTimeoutSeconds);
        }
    }

    record GatewayCredentialRequest(String credential) {

        @Override
        public String toString() {
            return "GatewayCredentialRequest[credential=<redacted>]";
        }
    }

    record TestGatewayRequest(
            AiGatewayPreset preset,
            AiGatewayProtocol protocol,
            String baseUrl,
            Integer requestTimeoutSeconds,
            String credential) {

        @Override
        public String toString() {
            return "TestGatewayRequest[preset=%s, protocol=%s, baseUrl=%s, requestTimeoutSeconds=%s, credential=<redacted>]"
                    .formatted(
                            preset,
                            protocol,
                            baseUrl,
                            requestTimeoutSeconds);
        }
    }

    record ProbeResponse(
            boolean authenticated,
            List<AiModelCatalogProbe.ModelRef> models,
            String errorCode) {

        static ProbeResponse from(AiModelCatalogProbe.Result result) {
            return new ProbeResponse(
                    result.authenticated(),
                    result.models(),
                    result.errorCode());
        }

        static ProbeResponse failed(String errorCode) {
            return new ProbeResponse(false, List.of(), errorCode);
        }
    }

    record SetRouteRequest(UUID gatewayProfileId, String modelId) {
    }

    record RouteResponse(
            AiWorkload workload,
            String gatewayKey,
            UUID gatewayProfileId,
            String modelId,
            String source,
            boolean editable,
            long version) {

        static RouteResponse override(AiRouteOverrideView route) {
            return new RouteResponse(
                    route.workload(),
                    route.gatewayKey(),
                    route.gatewayProfileId(),
                    route.modelId(),
                    "ORGANIZATION_OVERRIDE",
                    true,
                    route.version());
        }

        static RouteResponse deployment(
                AiWorkload workload,
                com.orgmemory.core.ai.AiRoute route) {
            return new RouteResponse(
                    workload,
                    route.gatewayId(),
                    null,
                    route.modelId(),
                    "DEPLOYMENT_DEFAULT",
                    workload == AiWorkload.ASSISTANT_CHAT
                            || workload == AiWorkload.PROMPT_EXECUTION,
                    0);
        }
    }

    record IndexSettingsResponse(
            String embeddingProvider,
            String embeddingModel,
            int dimensions,
            String distanceMetric,
            String managementMode,
            boolean editable,
            String lifecycleNote) {
    }
}
