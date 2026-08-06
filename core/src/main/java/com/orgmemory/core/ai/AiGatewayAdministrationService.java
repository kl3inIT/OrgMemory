package com.orgmemory.core.ai;

import com.orgmemory.core.permission.PermissionAuditCommand;
import com.orgmemory.core.permission.PermissionAuditDecision;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.BusinessConflictException;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import com.orgmemory.core.shared.secret.SecretCipher;
import com.orgmemory.core.shared.secret.SecretValue;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiGatewayAdministrationService {

    private static final Pattern GATEWAY_KEY =
            Pattern.compile("[a-z0-9][a-z0-9-]{0,62}[a-z0-9]");
    private static final int MIN_TIMEOUT_SECONDS = 1;
    private static final int MAX_TIMEOUT_SECONDS = 300;
    private static final int MAX_MODEL_ID_LENGTH = 200;
    private static final int MAX_ASSISTANT_MODELS = 50;
    private static final String POLICY_VERSION = "ai-control-plane-v1";

    private final AiGatewayProfileRepository profiles;
    private final AiGatewayCredentialRepository credentials;
    private final AiRouteOverrideRepository routes;
    private final AiAssistantModelActivationRepository assistantModels;
    private final AiGatewayEndpointPolicy endpoints;
    private final SecretCipher cipher;
    private final PermissionAuditService audit;

    AiGatewayAdministrationService(
            AiGatewayProfileRepository profiles,
            AiGatewayCredentialRepository credentials,
            AiRouteOverrideRepository routes,
            AiAssistantModelActivationRepository assistantModels,
            AiGatewayEndpointPolicy endpoints,
            SecretCipher cipher,
            PermissionAuditService audit) {
        this.profiles = profiles;
        this.credentials = credentials;
        this.routes = routes;
        this.assistantModels = assistantModels;
        this.endpoints = endpoints;
        this.cipher = cipher;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<AiGatewayProfileView> list(UUID organizationId) {
        return profiles.findByOrganizationIdOrderByDisplayNameAsc(organizationId)
                .stream()
                .map(this::view)
                .toList();
    }

    @Transactional(readOnly = true)
    public AiGatewayProfileView require(
            UUID organizationId,
            UUID profileId) {
        return view(requireProfile(organizationId, profileId));
    }

    @Transactional
    public AiGatewayProfileView create(
            UUID organizationId,
            String gatewayKey,
            String displayName,
            AiGatewayPreset preset,
            AiGatewayCategory category,
            AiGatewayProtocol protocol,
            String baseUrl,
            Integer requestTimeoutSeconds,
            boolean supportsOpenAiReasoningEffort,
            SecretValue credential,
            UUID adminUserId) {
        String key = requireGatewayKey(gatewayKey);
        if (profiles.findByOrganizationIdAndGatewayKey(organizationId, key)
                .isPresent()) {
            throw new BusinessConflictException(
                    "ai.gateway-key-exists",
                    "An AI gateway with this key already exists");
        }
        AiGatewayPreset requiredPreset =
                requireNonNull(preset, "provider preset");
        AiGatewayProtocol requiredProtocol =
                requireNonNull(protocol, "provider protocol");
        AiGatewayCategory requiredCategory =
                requireNonNull(category, "provider category");
        requirePresetShape(
                requiredPreset,
                requiredCategory,
                requiredProtocol);
        String allowedBaseUrl = endpoints.requireAllowed(
                requiredPreset,
                requiredProtocol,
                baseUrl);
        AiGatewayProfile profile = profiles.save(new AiGatewayProfile(
                organizationId,
                key,
                requireText(displayName, "display name", 120),
                requiredPreset,
                requiredCategory,
                requiredProtocol,
                allowedBaseUrl,
                requireTimeout(requestTimeoutSeconds),
                requireReasoningCapability(
                        requiredProtocol,
                        supportsOpenAiReasoningEffort),
                adminUserId));
        if (credential != null) {
            credentials.save(new AiGatewayCredential(
                    organizationId,
                    profile.getId(),
                    cipher.encrypt(credential),
                    adminUserId,
                    Instant.now()));
            profile.touch(adminUserId);
            profile = profiles.save(profile);
        }
        record(
                organizationId,
                adminUserId,
                "AI_GATEWAY_CREATE",
                profile.getId().toString(),
                "GATEWAY_CREATED");
        return view(profile);
    }

    public AiGatewayProfileView create(
            UUID organizationId,
            String gatewayKey,
            String displayName,
            AiGatewayPreset preset,
            AiGatewayCategory category,
            AiGatewayProtocol protocol,
            String baseUrl,
            Integer requestTimeoutSeconds,
            SecretValue credential,
            UUID adminUserId) {
        return create(
                organizationId,
                gatewayKey,
                displayName,
                preset,
                category,
                protocol,
                baseUrl,
                requestTimeoutSeconds,
                false,
                credential,
                adminUserId);
    }

    @Transactional
    public AiGatewayProfileView update(
            UUID organizationId,
            UUID profileId,
            String displayName,
            String baseUrl,
            Integer requestTimeoutSeconds,
            boolean supportsOpenAiReasoningEffort,
            SecretValue credential,
            UUID adminUserId) {
        AiGatewayProfile profile = requireProfile(organizationId, profileId);
        return updateProfile(
                organizationId,
                profileId,
                displayName,
                baseUrl,
                requestTimeoutSeconds,
                supportsOpenAiReasoningEffort,
                credential,
                adminUserId,
                profile);
    }

    private AiGatewayProfileView updateProfile(
            UUID organizationId,
            UUID profileId,
            String displayName,
            String baseUrl,
            Integer requestTimeoutSeconds,
            boolean supportsOpenAiReasoningEffort,
            SecretValue credential,
            UUID adminUserId,
            AiGatewayProfile profile) {
        if (profile.supportsOpenAiReasoningEffort()
                && !supportsOpenAiReasoningEffort
                && routes.existsByOrganizationIdAndGatewayProfileIdAndOpenAiReasoningEffortIsNotNull(
                        organizationId,
                        profileId)) {
            throw new BusinessConflictException(
                    "ai.openai-reasoning-in-use",
                    "Clear explicit reasoning effort from this gateway's routes before disabling support");
        }
        profile.update(
                requireText(displayName, "display name", 120),
                endpoints.requireAllowed(
                        profile.preset(),
                        profile.protocol(),
                        baseUrl),
                requireTimeout(requestTimeoutSeconds),
                requireReasoningCapability(
                        profile.protocol(),
                        supportsOpenAiReasoningEffort),
                adminUserId);
        if (credential != null) {
            replaceCredential(
                    organizationId,
                    profileId,
                    credential,
                    adminUserId);
            record(
                    organizationId,
                    adminUserId,
                    "AI_GATEWAY_CREDENTIAL",
                    profileId.toString(),
                    "CREDENTIAL_SET");
        }
        profile = profiles.save(profile);
        record(
                organizationId,
                adminUserId,
                "AI_GATEWAY_UPDATE",
                profileId.toString(),
                "GATEWAY_UPDATED");
        return view(profile);
    }

    @Transactional
    public AiGatewayProfileView update(
            UUID organizationId,
            UUID profileId,
            String displayName,
            String baseUrl,
            Integer requestTimeoutSeconds,
            SecretValue credential,
            UUID adminUserId) {
        AiGatewayProfile profile = requireProfile(organizationId, profileId);
        return updateProfile(
                organizationId,
                profileId,
                displayName,
                baseUrl,
                requestTimeoutSeconds,
                profile.supportsOpenAiReasoningEffort(),
                credential,
                adminUserId,
                profile);
    }

    @Transactional
    public void setCredential(
            UUID organizationId,
            UUID profileId,
            SecretValue credential,
            UUID adminUserId) {
        AiGatewayProfile profile = requireProfile(organizationId, profileId);
        replaceCredential(
                organizationId,
                profileId,
                requireNonNull(credential, "gateway credential"),
                adminUserId);
        profile.touch(adminUserId);
        profiles.save(profile);
        record(
                organizationId,
                adminUserId,
                "AI_GATEWAY_CREDENTIAL",
                profileId.toString(),
                "CREDENTIAL_SET");
    }

    private void replaceCredential(
            UUID organizationId,
            UUID profileId,
            SecretValue credential,
            UUID adminUserId) {
        Instant now = Instant.now();
        credentials.findByOrganizationIdAndGatewayProfileId(
                        organizationId,
                        profileId)
                .ifPresentOrElse(
                        stored -> {
                            stored.replace(
                                    cipher.encrypt(credential),
                                    adminUserId,
                                    now);
                            credentials.save(stored);
                        },
                        () -> credentials.save(new AiGatewayCredential(
                                organizationId,
                                profileId,
                                cipher.encrypt(credential),
                                adminUserId,
                                now)));
    }

    @Transactional
    public AiRouteOverrideView setRoute(
            UUID organizationId,
            AiWorkload workload,
            UUID profileId,
            String modelId,
            OpenAiReasoningEffort openAiReasoningEffort,
            UUID adminUserId) {
        requireEditableWorkload(workload);
        AiGatewayProfile profile = requireProfile(organizationId, profileId);
        if (!profile.enabled()) {
            throw new BusinessConflictException(
                    "ai.gateway-disabled",
                    "A disabled AI gateway cannot be selected");
        }
        if (credentials.findByOrganizationIdAndGatewayProfileId(
                        organizationId,
                        profileId)
                .isEmpty()) {
            throw new BusinessConflictException(
                    "ai.gateway-credential-missing",
                    "The selected AI gateway has no credential");
        }
        String requiredModel = requireText(
                modelId,
                "model id",
                MAX_MODEL_ID_LENGTH);
        if (openAiReasoningEffort != null
                && (profile.protocol() != AiGatewayProtocol.OPENAI_COMPATIBLE
                        || !profile.supportsOpenAiReasoningEffort())) {
            throw new BusinessValidationException(
                    "ai.openai-reasoning-unsupported",
                    "The selected AI gateway has not declared OpenAI reasoning effort support");
        }
        Instant now = Instant.now();
        AiRouteOverride override = routes
                .findByOrganizationIdAndWorkload(organizationId, workload)
                .orElseGet(() -> new AiRouteOverride(
                        organizationId,
                        workload,
                        profileId,
                        requiredModel,
                        openAiReasoningEffort,
                        adminUserId,
                        now));
        override.replace(
                profileId,
                requiredModel,
                openAiReasoningEffort,
                adminUserId,
                now);
        override = routes.save(override);
        record(
                organizationId,
                adminUserId,
                "AI_ROUTE_SET",
                workload.name(),
                "ROUTE_SET");
        return routeView(override, profile);
    }

    public AiRouteOverrideView setRoute(
            UUID organizationId,
            AiWorkload workload,
            UUID profileId,
            String modelId,
            UUID adminUserId) {
        return setRoute(
                organizationId,
                workload,
                profileId,
                modelId,
                null,
                adminUserId);
    }

    @Transactional
    public void clearRoute(
            UUID organizationId,
            AiWorkload workload,
            UUID adminUserId) {
        requireEditableWorkload(workload);
        routes.findByOrganizationIdAndWorkload(organizationId, workload)
                .ifPresent(routes::delete);
        record(
                organizationId,
                adminUserId,
                "AI_ROUTE_CLEAR",
                workload.name(),
                "DEPLOYMENT_DEFAULT_RESTORED");
    }

    @Transactional(readOnly = true)
    public List<AiRouteOverrideView> routes(UUID organizationId) {
        return routes.findByOrganizationIdOrderByWorkloadAsc(organizationId)
                .stream()
                .map(route -> routeView(
                        route,
                        requireProfile(
                                organizationId,
                                route.gatewayProfileId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<AiRouteOverrideView> route(
            UUID organizationId,
            AiWorkload workload) {
        return routes.findByOrganizationIdAndWorkload(organizationId, workload)
                .map(route -> routeView(
                        route,
                        requireProfile(
                                organizationId,
                                route.gatewayProfileId())));
    }

    @Transactional(readOnly = true)
    public List<AiAssistantModelActivationView> assistantModels(
            UUID organizationId,
            UUID profileId) {
        requireProfile(organizationId, profileId);
        return assistantModels
                .findAllByOrganizationIdAndGatewayProfileIdAndEnabledTrueOrderByDisplayNameAscModelIdAsc(
                        organizationId,
                        profileId)
                .stream()
                .map(AiAssistantModelActivation::view)
                .toList();
    }

    @Transactional
    public List<AiAssistantModelActivationView> replaceAssistantModels(
            UUID organizationId,
            UUID profileId,
            List<AiAssistantModelDefinition> requestedModels,
            UUID adminUserId) {
        AiGatewayProfile profile = profiles
                .findByIdAndOrganizationIdAndEnabledTrue(profileId, organizationId)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "ai.gateway-not-found",
                        "AI gateway not found",
                        BusinessErrorExposure.OPAQUE_RESOURCE));
        AiRouteOverride route = routes
                .findByOrganizationIdAndWorkload(
                        organizationId,
                        AiWorkload.ASSISTANT_CHAT)
                .orElseThrow(() -> new BusinessConflictException(
                        "ai.assistant-model-route-required",
                        "Set an organization Assistant route before enabling additional models"));
        if (!route.gatewayProfileId().equals(profileId)) {
            throw new BusinessConflictException(
                    "ai.assistant-model-gateway-inactive",
                    "Additional Assistant models must belong to the active Assistant gateway");
        }
        if (route.openAiReasoningEffort() != null
                && route.openAiReasoningEffort() != OpenAiReasoningEffort.NONE) {
            throw new BusinessConflictException(
                    "ai.assistant-model-options-incompatible",
                    "Additional Assistant models require provider-default or none reasoning options");
        }

        List<AiAssistantModelDefinition> normalized = normalizeAssistantModels(
                requestedModels,
                route.modelId());
        List<AiAssistantModelActivation> active = assistantModels
                .findAllByOrganizationIdAndGatewayProfileIdAndEnabledTrue(
                        organizationId,
                        profileId);
        java.util.Map<String, AiAssistantModelDefinition> requestedByModel = normalized.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AiAssistantModelDefinition::modelId,
                        model -> model));
        Instant now = Instant.now();
        active.stream()
                .filter(model -> {
                    AiAssistantModelDefinition requested = requestedByModel.get(model.modelId());
                    return requested == null
                            || !requested.displayName().equals(model.view().displayName());
                })
                .forEach(model -> model.disable(adminUserId, now));
        assistantModels.flush();
        java.util.Set<String> unchanged = active.stream()
                .filter(AiAssistantModelActivation::enabled)
                .map(AiAssistantModelActivation::modelId)
                .collect(java.util.stream.Collectors.toSet());
        normalized.stream()
                .filter(model -> !unchanged.contains(model.modelId()))
                .map(model -> new AiAssistantModelActivation(
                        organizationId,
                        profile.getId(),
                        model.modelId(),
                        model.displayName(),
                        adminUserId))
                .forEach(assistantModels::save);
        record(
                organizationId,
                adminUserId,
                "AI_ASSISTANT_MODEL_CATALOG",
                profileId.toString(),
                "CATALOG_REPLACED");
        return assistantModels(
                organizationId,
                profileId);
    }

    @Transactional(readOnly = true)
    public Optional<AiGatewayConnection> connection(
            UUID organizationId,
            String gatewayKey) {
        return profiles.findByOrganizationIdAndGatewayKey(
                        organizationId,
                        gatewayKey)
                .filter(AiGatewayProfile::enabled)
                .flatMap(profile -> credentials
                        .findByOrganizationIdAndGatewayProfileId(
                                organizationId,
                                profile.getId())
                        .map(credential -> new AiGatewayConnection(
                                organizationId,
                                profile.getId(),
                                profile.gatewayKey(),
                                profile.protocol(),
                                profile.supportsOpenAiReasoningEffort(),
                                endpoints.requireAllowed(
                                        profile.preset(),
                                        profile.protocol(),
                                        profile.baseUrl()),
                                cipher.decrypt(credential.stored()),
                                Duration.ofSeconds(
                                        profile.requestTimeoutSeconds()),
                                profile.cacheVersion())));
    }

    @Transactional
    public void disable(
            UUID organizationId,
            UUID profileId,
            UUID adminUserId) {
        AiGatewayProfile profile = requireProfile(organizationId, profileId);
        if (routes.existsByOrganizationIdAndGatewayProfileId(
                organizationId,
                profileId)) {
            throw new BusinessConflictException(
                    "ai.gateway-in-use",
                    "Change routes before disabling this AI gateway");
        }
        profile.disable(adminUserId);
        profiles.save(profile);
        record(
                organizationId,
                adminUserId,
                "AI_GATEWAY_DISABLE",
                profileId.toString(),
                "GATEWAY_DISABLED");
    }

    @Transactional
    public void recordProbe(
            UUID organizationId,
            UUID adminUserId,
            String target,
            boolean authenticated,
            String errorCode) {
        String reason = authenticated
                ? "PROBE_SUCCEEDED"
                : "PROBE_FAILED_"
                        + (errorCode == null || errorCode.isBlank()
                                ? "UNKNOWN"
                                : errorCode.strip()
                                        .toUpperCase(Locale.ROOT)
                                        .replaceAll("[^A-Z0-9_]", "_"));
        record(
                organizationId,
                adminUserId,
                "AI_GATEWAY_TEST",
                requireText(target, "probe target", 200),
                reason);
    }

    private AiGatewayProfile requireProfile(
            UUID organizationId,
            UUID profileId) {
        if (profileId == null) {
            throw new BusinessValidationException(
                    "ai.gateway-id-required",
                    "An AI gateway id is required");
        }
        return profiles.findByIdAndOrganizationId(profileId, organizationId)
                .orElseThrow(() -> new BusinessNotFoundException(
                        "ai.gateway-not-found",
                        "The AI gateway is not available",
                        BusinessErrorExposure.OPAQUE_RESOURCE));
    }

    private AiGatewayProfileView view(AiGatewayProfile profile) {
        Optional<AiGatewayCredential> credential =
                credentials.findByOrganizationIdAndGatewayProfileId(
                        profile.organizationId(),
                        profile.getId());
        return new AiGatewayProfileView(
                profile.getId(),
                profile.gatewayKey(),
                profile.displayName(),
                profile.preset(),
                profile.category(),
                profile.protocol(),
                profile.supportsOpenAiReasoningEffort(),
                profile.baseUrl(),
                profile.requestTimeoutSeconds(),
                profile.enabled(),
                profile.cacheVersion(),
                credential.isPresent(),
                credential.map(AiGatewayCredential::setByUserId).orElse(null),
                credential.map(AiGatewayCredential::setAt).orElse(null),
                profile.createdByUserId(),
                profile.updatedByUserId(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }

    private static AiRouteOverrideView routeView(
            AiRouteOverride route,
            AiGatewayProfile profile) {
        return new AiRouteOverrideView(
                route.getId(),
                route.workload(),
                route.gatewayProfileId(),
                profile.gatewayKey(),
                route.modelId(),
                route.openAiReasoningEffort(),
                route.getVersion(),
                route.setByUserId(),
                route.setAt());
    }

    private void record(
            UUID organizationId,
            UUID adminUserId,
            String operation,
            String target,
            String reason) {
        audit.record(new PermissionAuditCommand(
                organizationId,
                adminUserId,
                operation,
                "AI_GATEWAY",
                target,
                PermissionAuditDecision.ALLOW,
                reason,
                POLICY_VERSION,
                null,
                null));
    }

    private static String requireGatewayKey(String value) {
        String normalized = requireText(value, "gateway key", 64)
                .toLowerCase(Locale.ROOT);
        if (normalized.length() < 2
                || !GATEWAY_KEY.matcher(normalized).matches()) {
            throw new BusinessValidationException(
                    "ai.gateway-key-invalid",
                    "Gateway key must contain lowercase letters, numbers, or hyphens");
        }
        return normalized;
    }

    private static List<AiAssistantModelDefinition> normalizeAssistantModels(
            List<AiAssistantModelDefinition> requestedModels,
            String defaultModelId) {
        List<AiAssistantModelDefinition> supplied = requestedModels == null
                ? List.of()
                : requestedModels;
        if (supplied.size() > MAX_ASSISTANT_MODELS) {
            throw new BusinessValidationException(
                    "ai.assistant-model-limit",
                    "At most 50 additional Assistant models may be enabled");
        }
        java.util.LinkedHashMap<String, AiAssistantModelDefinition> normalized =
                new java.util.LinkedHashMap<>();
        for (AiAssistantModelDefinition model : supplied) {
            if (model == null) {
                throw new BusinessValidationException(
                        "ai.assistant-model-invalid",
                        "Assistant model entries must be complete");
            }
            String modelId = requireText(
                    model.modelId(),
                    "Assistant model id",
                    MAX_MODEL_ID_LENGTH);
            if (modelId.equals(defaultModelId)) {
                continue;
            }
            String displayName = requireText(
                    model.displayName() == null || model.displayName().isBlank()
                            ? modelId
                            : model.displayName(),
                    "Assistant model display name",
                    MAX_MODEL_ID_LENGTH);
            normalized.putIfAbsent(
                    modelId,
                    new AiAssistantModelDefinition(modelId, displayName));
        }
        return List.copyOf(normalized.values());
    }

    private static int requireTimeout(Integer value) {
        int timeout = value == null ? 60 : value;
        if (timeout < MIN_TIMEOUT_SECONDS || timeout > MAX_TIMEOUT_SECONDS) {
            throw new BusinessValidationException(
                    "ai.gateway-timeout-invalid",
                    "AI gateway timeout must be between 1 and 300 seconds");
        }
        return timeout;
    }

    private static String requireText(
            String value,
            String label,
            int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessValidationException(
                    "ai.value-required",
                    "AI " + label + " is required");
        }
        String normalized = value.strip();
        if (normalized.length() > maximumLength) {
            throw new BusinessValidationException(
                    "ai.value-too-long",
                    "AI " + label + " is too long");
        }
        return normalized;
    }

    private static <T> T requireNonNull(T value, String label) {
        if (value == null) {
            throw new BusinessValidationException(
                    "ai.value-required",
                    "AI " + label + " is required");
        }
        return value;
    }

    private static void requirePresetShape(
            AiGatewayPreset preset,
            AiGatewayCategory category,
            AiGatewayProtocol protocol) {
        AiGatewayCategory expectedCategory = switch (preset) {
            case OPENAI, ANTHROPIC -> AiGatewayCategory.DIRECT_PROVIDER;
            case NINE_ROUTER, OPENROUTER, LITELLM ->
                AiGatewayCategory.GATEWAY_ROUTER;
            case OLLAMA, OPENAI_COMPATIBLE ->
                AiGatewayCategory.SELF_HOSTED_CUSTOM;
        };
        AiGatewayProtocol expectedProtocol = preset == AiGatewayPreset.ANTHROPIC
                ? AiGatewayProtocol.ANTHROPIC_MESSAGES
                : AiGatewayProtocol.OPENAI_COMPATIBLE;
        if (category != expectedCategory || protocol != expectedProtocol) {
            throw new BusinessValidationException(
                    "ai.gateway-preset-invalid",
                    "AI gateway preset, category, and protocol do not match");
        }
    }

    private static boolean requireReasoningCapability(
            AiGatewayProtocol protocol,
            boolean supportsOpenAiReasoningEffort) {
        if (supportsOpenAiReasoningEffort
                && protocol != AiGatewayProtocol.OPENAI_COMPATIBLE) {
            throw new BusinessValidationException(
                    "ai.openai-reasoning-protocol-invalid",
                    "OpenAI reasoning effort requires an OpenAI-compatible gateway");
        }
        return supportsOpenAiReasoningEffort;
    }

    private static void requireEditableWorkload(AiWorkload workload) {
        if (workload != AiWorkload.ASSISTANT_CHAT
                && workload != AiWorkload.PROMPT_EXECUTION
                && workload != AiWorkload.KEYWORD_PLANNING) {
            throw new BusinessValidationException(
                    "ai.route-not-editable",
                    "This AI workload is deployment-managed");
        }
    }
}
