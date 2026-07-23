package com.orgmemory.core.knowledge;

import java.util.UUID;

/**
 * One connection that could not be crawled this poll.
 *
 * <p>{@code errorCode} is the source's own vocabulary where it has one — Slack answers
 * {@code invalid_auth}, {@code token_revoked}, {@code missing_scope} — because that is what an
 * administrator can look up. {@code message} is a diagnostic and must never carry the
 * credential: adapters authenticate through a header and report the method and the error, so
 * nothing that reaches here has ever held a secret.
 */
public record ConnectorConnectionFailure(
        UUID organizationId,
        String sourceSystem,
        String sourceConnectionKey,
        String errorCode,
        String message) {
}
