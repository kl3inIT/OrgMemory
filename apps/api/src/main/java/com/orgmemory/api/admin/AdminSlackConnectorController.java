package com.orgmemory.api.admin;

import com.orgmemory.connectors.slack.SlackCredentialProbe;
import com.orgmemory.connectors.slack.SlackCredentialProbeResult;
import com.orgmemory.core.knowledge.SourceConnectionAdminService;
import com.orgmemory.core.knowledge.SourceConnectionConfigurationView;
import com.orgmemory.core.knowledge.SourceIdentityTrust;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.secret.SecretValue;
import io.swagger.v3.oas.annotations.Operation;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

/**
 * Configuring the Slack connection from the browser: which workspace, with which token, into
 * which Knowledge Space, on what cadence. This is what replaces editing environment variables
 * and restarting the worker.
 *
 * <p>The token goes in and never comes back out. Onyx returns stored credentials to its admin
 * screen masked to first and last four characters, and lets an operator switch even that off;
 * nothing here returns the token in any form, because the administrator who set it already had
 * it and no screen has a use for a value it cannot show. What the screen gets instead is whether
 * a credential exists, who set it, and when.
 */
@RestController
@RequestMapping("/api/admin/connectors/slack")
class AdminSlackConnectorController {

    private static final String SOURCE_SYSTEM = "slack";
    private static final long DEFAULT_CONTENT_CRAWL_INTERVAL_SECONDS = 3600;
    private static final int DEFAULT_MAX_THREADS_PER_CHANNEL = 500;

    private final AdminAccessGuard guard;
    private final SourceConnectionAdminService connections;
    private final SlackCredentialProbe probe;

    AdminSlackConnectorController(
            AdminAccessGuard guard, SourceConnectionAdminService connections, SlackCredentialProbe probe) {
        this.guard = guard;
        this.connections = connections;
        this.probe = probe;
    }

    /** A connection as the screen sees it. There is no field for the token and never will be. */
    record AdminSlackConnectionResponse(
            String sourceConnectionKey,
            SourceIdentityTrust identityTrust,
            boolean crawlEnabled,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            List<String> channels,
            long contentCrawlIntervalSeconds,
            int maxThreadsPerChannel,
            boolean credentialSet,
            UUID credentialSetByUserId,
            Instant credentialSetAt,
            UUID configuredByUserId,
            Instant configuredAt) {

        static AdminSlackConnectionResponse from(SourceConnectionConfigurationView view) {
            return new AdminSlackConnectionResponse(
                    view.sourceConnectionKey(),
                    view.identityTrust(),
                    view.crawlEnabled(),
                    view.knowledgeSpaceId(),
                    view.actorUserId(),
                    view.channels(),
                    view.contentCrawlIntervalSeconds(),
                    view.maxThreadsPerChannel(),
                    view.credentialSet(),
                    view.credentialSetByUserId(),
                    view.credentialSetAt(),
                    view.configuredByUserId(),
                    view.configuredAt());
        }
    }

    record ConfigureCrawlRequest(
            boolean crawlEnabled,
            UUID knowledgeSpaceId,
            UUID actorUserId,
            List<String> channels,
            Long contentCrawlIntervalSeconds,
            Integer maxThreadsPerChannel) {
    }

    /**
     * Carries a bot token in, on the two paths that legitimately accept one.
     *
     * <p>{@code toString} is overridden because a record generates one that prints every field,
     * and a request object reaches a log the moment anything goes wrong with it — a binding
     * failure, a filter that traces payloads, a debugger left on. Onyx guards the same edge by
     * raising on {@code str()} of a sensitive value.
     */
    record SlackTokenRequest(String botToken) {

        @Override
        public String toString() {
            return "SlackTokenRequest[botToken=<redacted>]";
        }
    }

    /**
     * What a token turned out to be. {@code errorCode} is Slack's own vocabulary
     * ({@code invalid_auth}, {@code missing_scope}, {@code token_revoked}), plus
     * {@code no_credential} when this connection has nothing stored to test.
     */
    record AdminSlackProbeResponse(
            boolean authenticated,
            String workspaceName,
            String workspaceId,
            String botName,
            boolean canListChannels,
            String errorCode) {

        static AdminSlackProbeResponse from(SlackCredentialProbeResult result) {
            return new AdminSlackProbeResponse(
                    result.authenticated(),
                    result.workspaceName(),
                    result.workspaceId(),
                    result.botName(),
                    result.canListChannels(),
                    result.errorCode());
        }

        static AdminSlackProbeResponse noCredential() {
            return new AdminSlackProbeResponse(false, null, null, null, false, "no_credential");
        }
    }

    @GetMapping
    @Operation(operationId = "listAdminSlackConnections", summary = "List Slack connections and their crawl settings")
    List<AdminSlackConnectionResponse> connections(Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        return connections.list(actor.organizationId(), SOURCE_SYSTEM).stream()
                .map(AdminSlackConnectionResponse::from)
                .toList();
    }

    @PutMapping("/{connectionKey}")
    @Operation(operationId = "configureAdminSlackConnection", summary = "Record how a Slack workspace is crawled")
    AdminSlackConnectionResponse configure(
            @PathVariable String connectionKey,
            @RequestBody ConfigureCrawlRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        return AdminSlackConnectionResponse.from(connections.configure(
                actor.organizationId(),
                SOURCE_SYSTEM,
                connectionKey,
                request.crawlEnabled(),
                request.knowledgeSpaceId(),
                request.actorUserId(),
                request.channels() == null ? List.of() : request.channels(),
                Duration.ofSeconds(request.contentCrawlIntervalSeconds() == null
                        ? DEFAULT_CONTENT_CRAWL_INTERVAL_SECONDS
                        : request.contentCrawlIntervalSeconds()),
                request.maxThreadsPerChannel() == null
                        ? DEFAULT_MAX_THREADS_PER_CHANNEL
                        : request.maxThreadsPerChannel(),
                actor.userId()));
    }

    /** Replaces the stored token. The response body is empty because there is nothing to echo. */
    @PutMapping("/{connectionKey}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "setAdminSlackCredential", summary = "Store a Slack bot token for a connection")
    void setCredential(
            @PathVariable String connectionKey,
            @RequestBody SlackTokenRequest request,
            Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        connections.setCredential(
                actor.organizationId(),
                SOURCE_SYSTEM,
                connectionKey,
                SecretValue.of(request.botToken()),
                actor.userId());
    }

    @DeleteMapping("/{connectionKey}/credential")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "forgetAdminSlackCredential", summary = "Forget a connection's stored Slack token")
    void forgetCredential(@PathVariable String connectionKey, Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        connections.forgetCredential(actor.organizationId(), SOURCE_SYSTEM, connectionKey, actor.userId());
    }

    /**
     * Checks a token that has not been stored, which is how a connection gets configured at all:
     * the workspace id this returns is the connection key, so an administrator pastes a token and
     * gets the key rather than having to go and look it up in Slack.
     */
    @PostMapping("/test")
    @Operation(operationId = "testAdminSlackToken", summary = "Check a Slack bot token without storing it")
    AdminSlackProbeResponse test(@RequestBody SlackTokenRequest request, Authentication authentication) {
        guard.requireAdministrator(authentication);
        return AdminSlackProbeResponse.from(probe.probe(SecretValue.of(request.botToken())));
    }

    /**
     * Checks the token already stored for a connection. A token that worked when it was set can
     * stop working without anything here changing — revoked in Slack, app reinstalled, scope
     * removed — so this exists to answer that without an administrator having to paste it again.
     */
    @PostMapping("/{connectionKey}/test")
    @Operation(operationId = "testAdminSlackConnection", summary = "Check a connection's stored Slack token")
    AdminSlackProbeResponse testStored(@PathVariable String connectionKey, Authentication authentication) {
        CurrentActor actor = guard.requireAdministrator(authentication);
        Optional<SecretValue> stored =
                connections.resolveCredential(actor.organizationId(), SOURCE_SYSTEM, connectionKey);
        return stored.map(token -> AdminSlackProbeResponse.from(probe.probe(token)))
                .orElseGet(AdminSlackProbeResponse::noCredential);
    }
}
