package com.orgmemory.core.knowledge;

/**
 * How far an observed identity from one source connection may be trusted. The
 * decision is per connection because it is a property of the source workspace —
 * whether it is SSO/SCIM provisioned — not of any individual user.
 *
 * <p>This is one of two signals that can carry an email join, not a gate over the
 * other. A source that confirms address ownership before an account can exist
 * already vouches per principal, and an untrusted connection does not override
 * that. The decision matters for the sources that cannot vouch for themselves,
 * where an administrator is the right party to answer instead of an adapter.
 */
public enum SourceIdentityTrust {

    /** No administrator has attested this connection; only what the source itself vouches counts. */
    UNTRUSTED,

    /** The workspace authenticates through the same IdP, so observed emails may carry a join. */
    SSO_VERIFIED
}
