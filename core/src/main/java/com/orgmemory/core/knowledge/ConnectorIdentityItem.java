package com.orgmemory.core.knowledge;

import com.orgmemory.core.knowledge.acl.SourcePrincipalKind;

/**
 * One source-owned identity observed by a crawl. Identity observation and membership capture
 * are deliberately separate contracts: seeing a group says nothing about whether its member
 * list was captured completely.
 *
 * @param kind             SOURCE_USER or SOURCE_GROUP
 * @param nativePrincipalId the source's immutable id for this principal
 * @param email            observed email for a user, if any (used by the SSO-email matcher)
 * @param displayName      observed display name, if any
 * @param ssoVerified      whether the source vouches the email was SSO-verified
 * @param idpIssuer        OIDC issuer for the trusted IdP-join matcher, if the source knows it
 * @param idpSubject       OIDC subject for the trusted IdP-join matcher, if the source knows it
 */
public record ConnectorIdentityItem(
        SourcePrincipalKind kind,
        String nativePrincipalId,
        String email,
        String displayName,
        boolean ssoVerified,
        String idpIssuer,
        String idpSubject) {

    public ConnectorIdentityItem {
        if (kind == null) {
            throw new IllegalArgumentException("connector identity kind is required");
        }
        if (nativePrincipalId == null || nativePrincipalId.isBlank()) {
            throw new IllegalArgumentException("connector identity nativePrincipalId is required");
        }
        nativePrincipalId = nativePrincipalId.trim();
    }
}
