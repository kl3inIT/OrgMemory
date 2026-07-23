package com.orgmemory.api.admin;

import com.orgmemory.connectors.slack.SlackCredentialProbe;
import com.orgmemory.connectors.slack.SlackCredentialProbeResult;
import com.orgmemory.core.knowledge.ConnectorCrawlAttemptView;
import com.orgmemory.core.knowledge.ConnectorSourceProfile;
import com.orgmemory.core.knowledge.ConnectorSourceRegistry;
import com.orgmemory.core.knowledge.SourceConnectionActivityService;
import com.orgmemory.core.knowledge.SourceConnectionActivityView;
import com.orgmemory.core.knowledge.SourceConnectionAdminService;
import com.orgmemory.core.knowledge.SourceConnectionConfigurationView;
import com.orgmemory.core.knowledge.SourceIdentityTrust;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.secret.SecretValue;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * Configuring source connections from the browser: which workspace, with which credential, into
 * which Knowledge Space, on what cadence. This is what replaces editing environment variables
 * and restarting the worker.
 *
 * <p>The path is the source system rather than a source's name, and the settings only that
 * source understands travel as an opaque object. Adding a connector adds an adapter and a
 * browser descriptor; it does not add an endpoint.
 *
 * <p>The credential goes in and never comes back out. Onyx returns stored credentials to its
 * admin screen masked to first and last four characters, and lets an operator turn even that
 * off; nothing here returns the token in any form, because the administrator who set it already
 * had it and no screen has a use for a value it cannot show. What the screen gets instead is
 * whether a credential exists, who set it, and when.
 */
@RestController
@RequestMapping("/api/admin/connectors")
class AdminConnectorController {

    private static final long DEFAULT_CONTENT_CRAWL_INTERVAL_SECONDS = 3600;
    private static final String SLACK = "slack";

    private final AdminAccessGuard guard;
    private final SourceConnectionAdminService connections;
    private final SourceConnectionActivityService activity;
    private final ConnectorSourceRegistry sources;
    private final SlackCredentialProbe slackProbe;
    private final ObjectMapper objectMapper = new ObjectMapper();

    AdminConnectorController(
            AdminAccessGuard guard,
            SourceConnectionAdminService connections,
            SourceConnectionActivityService activity,
            ConnectorSourceRegistry sources,
            SlackCredentialProbe slackProbe) {
        this.guard = guard;
        this.connections = connections;
        this.activity = activity;
        this.sources = sources;
        this.slackProbe = slackProbe;
    }

    /** A source this deployment can actually ingest, as reported by the adapters installed. */
    record AdminConnectorSourceResponse(String sourceSystem, String displayName) {

        static AdminConnectorSourceResponse from(ConnectorSourceProfile profile) {
            return new AdminConnectorSourceResponse(profile.sourceSystem(), profile.displayName());
        }
    }

    /** A connection as the screen sees it. There is no field for the token and never will be. */
    record AdminConnectionResponse(
            String sourceSystem,
            String sourceConnectionKey,
            SourceIdentityTrust identityTrust,
            boolean crawlEnabled,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            Map<String, Object> sourceConfig,
            long contentCrawlIntervalSeconds,
            boolean credentialSet,
            UUID credentialSetByUserId,
            Instant credentialSetAt,
            UUID configuredByUserId,
            Instant configuredAt) {
    }

    /** What a connection has in the ledger and how its recent crawls went. */
    record AdminConnectionActivityResponse(
            String sourceSystem,
            String sourceConnectionKey,
            long objectsTotal,
            long objectsActive,
            long objectsArchived,
            Instant lastObjectAt,
            Instant lastCrawlAt,
            List<AdminCrawlAttemptResponse> recentAttempts) {
    }

    /**
     * One crawl attempt. {@code outcome} distinguishes a batch that reconciled, one refused for
     * good, one that will be retried, and a connection that produced no batch at all — which is
     * what a revoked credential looks like and is the reading an administrator needs most.
     */
    record AdminCrawlAttemptResponse(
            String outcome,
            int objectsMaterialized,
            int objectsRotated,
            int objectsRematerialized,
            int objectsRetired,
            int objectsFailed,
            String errorCode,
            String errorMessage,
            Instant attemptedAt) {

        static AdminCrawlAttemptResponse from(ConnectorCrawlAttemptView attempt) {
            return new AdminCrawlAttemptResponse(
                    attempt.outcome().name(),
                    attempt.objectsMaterialized(),
                    attempt.objectsRotated(),
                    attempt.objectsRematerialized(),
                    attempt.objectsRetired(),
                    attempt.objectsFailed(),
                    attempt.errorCode(),
                    attempt.errorMessage(),
                    attempt.attemptedAt());
        }
    }

    record ConfigureConnectionRequest(
            boolean crawlEnabled,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            Map<String, Object> sourceConfig,
            Long contentCrawlIntervalSeconds) {
    }

    /**
     * Carries a credential in, on the two paths that legitimately accept one.
     *
     * <p>{@code toString} is overridden because a record generates one that prints every field,
     * and a request object reaches a log the moment anything goes wrong with it — a binding
     * failure, a filter that traces payloads, a debugger left on. Onyx guards the same edge by
     * raising on {@code str()} of a sensitive value.
     */
    record ConnectorCredentialRequest(String botToken) {

        @Override
        public String toString() {
            return "ConnectorCredentialRequest[botToken=<redacted>]";
        }
    }

    /**
     * What a credential turned out to be. {@code errorCode} is the source's own vocabulary
     * ({@code invalid_auth}, {@code missing_scope}, {@code token_revoked}), plus
     * {@code no_credential} when this connection has nothing stored to test.
     */
    record AdminConnectorProbeResponse(
            boolean authenticated,
            String workspaceName,
            String workspaceId,
            String botName,
            boolean canListChannels,
            String errorCode) {

        static AdminConnectorProbeResponse from(SlackCredentialProbeResult result) {
            return new AdminConnectorProbeResponse(
                    result.authenticated(),
                    result.workspaceName(),
                    result.workspaceId(),
                    result.botName(),
                    result.canListChannels(),
                    result.errorCode());
        }

        static AdminConnectorProbeResponse noCredential() {
            return new AdminConnectorProbeResponse(false, null, null, null, false, "no_credential");
        }
    }

    @GetMapping("/sources")
    @Operation(operationId = "listAdminConnectorSources", summary = "List the sources this deployment can ingest")
    List<AdminConnectorSourceResponse> installedSources(Authentication authentication) {
        guard.requireAdministrator(authentication);
        return sources.installed().stream().map(AdminConnectorSourceResponse::from).toList();
    }

    @GetMapping("/{sourceSystem}")
    @Operation(operationId = "listAdminConnections", summary = "List a source's connections and their crawl settings")
    List<AdminConnectionResponse> connections(@PathVariable String sourceSystem, Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        String system = requireInstalled(sourceSystem);
        return connections.list(actor.organizationId(), system).stream().map(this::toResponse).toList();
    }

    /**
     * What this connection has done, as against what it was told to do.
     *
     * <p>Separate from the configuration it sits beside because it answers the question a
     * configuration screen cannot: a connection can read as enabled, pointed at a Space and
     * holding a credential, and still be producing nothing. The attempts say why.
     */
    @GetMapping("/{sourceSystem}/{connectionKey}/activity")
    @Operation(operationId = "getAdminConnectionActivity", summary = "Read what a connection has crawled and what went wrong")
    AdminConnectionActivityResponse activity(
            @PathVariable String sourceSystem,
            @PathVariable String connectionKey,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        SourceConnectionActivityView view = activity.describe(
                actor.organizationId(), requireInstalled(sourceSystem), connectionKey);
        return new AdminConnectionActivityResponse(
                view.sourceSystem(),
                view.sourceConnectionKey(),
                view.objectsTotal(),
                view.objectsActive(),
                view.objectsArchived(),
                view.lastObjectAt(),
                view.lastCheckpointAt(),
                view.recentAttempts().stream().map(AdminCrawlAttemptResponse::from).toList());
    }

    @PutMapping("/{sourceSystem}/{connectionKey}")
    @Operation(operationId = "configureAdminConnection", summary = "Record how a connection is crawled")
    AdminConnectionResponse configure(
            @PathVariable String sourceSystem,
            @PathVariable String connectionKey,
            @RequestBody ConfigureConnectionRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        String system = requireInstalled(sourceSystem);
        return toResponse(connections.configure(
                actor.organizationId(),
                system,
                connectionKey,
                request.crawlEnabled(),
                request.knowledgeSpaceId(),
                request.actorUserId(),
                writeConfig(request.sourceConfig()),
                Duration.ofSeconds(request.contentCrawlIntervalSeconds() == null
                        ? DEFAULT_CONTENT_CRAWL_INTERVAL_SECONDS
                        : request.contentCrawlIntervalSeconds()),
                actor.userId()));
    }

    /** Replaces the stored credential. The response body is empty because there is nothing to echo. */
    @PutMapping("/{sourceSystem}/{connectionKey}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "setAdminConnectionCredential", summary = "Store a credential for a connection")
    void setCredential(
            @PathVariable String sourceSystem,
            @PathVariable String connectionKey,
            @RequestBody ConnectorCredentialRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        connections.setCredential(
                actor.organizationId(),
                requireInstalled(sourceSystem),
                connectionKey,
                SecretValue.of(request.botToken()),
                actor.userId());
    }

    @DeleteMapping("/{sourceSystem}/{connectionKey}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "forgetAdminConnectionCredential", summary = "Forget a connection's stored credential")
    void forgetCredential(
            @PathVariable String sourceSystem,
            @PathVariable String connectionKey,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        connections.forgetCredential(
                actor.organizationId(), requireInstalled(sourceSystem), connectionKey, actor.userId());
    }

    /**
     * Checks a credential that has not been stored, which is how a Slack connection gets
     * configured at all: the workspace id this returns is the connection key, so an
     * administrator pastes a token and gets the key rather than looking it up in Slack.
     */
    @PostMapping("/{sourceSystem}/test")
    @Operation(operationId = "testAdminConnectorCredential", summary = "Check a credential without storing it")
    AdminConnectorProbeResponse test(
            @PathVariable String sourceSystem,
            @RequestBody ConnectorCredentialRequest request,
            Authentication authentication) {
        guard.requireAdministrator(authentication);
        return AdminConnectorProbeResponse.from(
                probe(requireInstalled(sourceSystem), SecretValue.of(request.botToken())));
    }

    /**
     * Checks the credential already stored for a connection. One that worked when it was set can
     * stop working without anything here changing — revoked at the source, app reinstalled, scope
     * removed — so this exists to answer that without pasting it again.
     */
    @PostMapping("/{sourceSystem}/{connectionKey}/test")
    @Operation(operationId = "testAdminConnection", summary = "Check a connection's stored credential")
    AdminConnectorProbeResponse testStored(
            @PathVariable String sourceSystem,
            @PathVariable String connectionKey,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        String system = requireInstalled(sourceSystem);
        Optional<SecretValue> stored =
                connections.resolveCredential(actor.organizationId(), system, connectionKey);
        return stored.map(token -> AdminConnectorProbeResponse.from(probe(system, token)))
                .orElseGet(AdminConnectorProbeResponse::noCredential);
    }

    /**
     * Only Slack can be probed today, because only Slack has an adapter. The generic answer is a
     * probe port each adapter contributes alongside its profile; that is worth building when a
     * second source needs it, and pretending it exists now would be one indirection over one
     * implementation.
     */
    private SlackCredentialProbeResult probe(String sourceSystem, SecretValue credential) {
        if (!SLACK.equals(sourceSystem)) {
            throw new IllegalArgumentException(
                    "Checking a credential is not implemented for the source system " + sourceSystem);
        }
        return slackProbe.probe(credential);
    }

    /** A source no adapter contributed has nothing to configure, so naming it is a request error. */
    private String requireInstalled(String sourceSystem) {
        return sources.require(sourceSystem).sourceSystem();
    }

    private AdminConnectionResponse toResponse(SourceConnectionConfigurationView view) {
        return new AdminConnectionResponse(
                view.sourceSystem(),
                view.sourceConnectionKey(),
                view.identityTrust(),
                view.crawlEnabled(),
                view.knowledgeSpaceId(),
                view.actorUserId(),
                readConfig(view.sourceConfig()),
                view.contentCrawlIntervalSeconds(),
                view.credentialSet(),
                view.credentialSetByUserId(),
                view.credentialSetAt(),
                view.configuredByUserId(),
                view.configuredAt());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readConfig(String sourceConfig) {
        if (sourceConfig == null || sourceConfig.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(sourceConfig, Map.class);
        } catch (RuntimeException unreadable) {
            // Stored by an older shape or hand-edited. Reporting it as empty keeps the screen
            // usable and lets an administrator overwrite it, which failing the request would not.
            return Map.of();
        }
    }

    private String writeConfig(Map<String, Object> sourceConfig) {
        return objectMapper.writeValueAsString(sourceConfig == null ? Map.of() : sourceConfig);
    }
}
