package com.orgmemory.core.knowledge;

import java.util.List;

/**
 * One external identity observed by a crawl — a source user or a source group. Observation
 * grants nothing on its own; a {@code SOURCE_USER} only confers access once the matcher
 * resolves it to a verified internal user, and a {@code SOURCE_GROUP} only through its
 * sealed membership. For a group, {@link #memberExternalKeys()} lists the external keys of
 * its member users at crawl time.
 *
 * @param kind             SOURCE_USER or SOURCE_GROUP
 * @param externalKey      the source's stable id (Slack user id, channel id)
 * @param email            observed email for a user, if any (used by the SSO-email matcher)
 * @param displayName      observed display name, if any
 * @param ssoVerified      whether the source vouches the email was SSO-verified
 * @param idpIssuer        OIDC issuer for the trusted IdP-join matcher, if the source knows it
 * @param idpSubject       OIDC subject for the trusted IdP-join matcher, if the source knows it
 * @param memberExternalKeys for a group, the external keys of its members; empty for a user
 */
public record ConnectorIdentityItem(
        SourcePrincipalKind kind,
        String externalKey,
        String email,
        String displayName,
        boolean ssoVerified,
        String idpIssuer,
        String idpSubject,
        List<String> memberExternalKeys) {

    public ConnectorIdentityItem {
        if (kind == null) {
            throw new IllegalArgumentException("connector identity kind is required");
        }
        if (externalKey == null || externalKey.isBlank()) {
            throw new IllegalArgumentException("connector identity externalKey is required");
        }
        externalKey = externalKey.trim();
        memberExternalKeys = memberExternalKeys == null ? List.of() : List.copyOf(memberExternalKeys);
        if (kind == SourcePrincipalKind.SOURCE_USER && !memberExternalKeys.isEmpty()) {
            throw new IllegalArgumentException("a SOURCE_USER identity cannot declare members");
        }
    }
}
