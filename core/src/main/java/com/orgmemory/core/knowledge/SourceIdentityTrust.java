package com.orgmemory.core.knowledge;

/**
 * How far an observed identity from one source connection may be trusted. The
 * decision is per connection because it is a property of the source workspace —
 * whether it is SSO/SCIM provisioned — not of any individual user.
 */
public enum SourceIdentityTrust {

    /** Observed emails are unverified claims; only IDP_JOIN or ADMIN_CONFIRMED may map. */
    UNTRUSTED,

    /** The workspace authenticates through the same IdP, so SSO_EMAIL_JOIN may fire. */
    SSO_VERIFIED
}
