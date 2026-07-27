# Keycloak 26.7 SCIM Alternative Spike

Date: 2026-07-27

## Result

Keycloak SCIM remains rejected as OrgMemory's production provisioning
authority. It is a preview, disabled-by-default, realm-scoped API. A shared
realm credential with `manage-users` has broader reach than one OrgMemory
Organization, while OrgMemory requires tenant ownership to come from one
connection credential.

The supported server configuration is `--features=scim-api`, followed by
enabling `scimApiEnabled` per realm. Only confidential clients are supported.
Full User and Group mutation requires the realm-management `manage-users` role
and an audience equal to the realm SCIM base URL. Deleting a User returns 204
and deletes the Keycloak user; it does not preserve OrgMemory's required actor,
binding, ownership, and tombstone history.

Disabling the preview feature removes the SCIM route, which is a useful
emergency cutoff but not an application-level organization kill switch.
Because this alternative is not selected, no Keycloak preview dependency or
production upgrade contract is introduced.

## Live Evidence

An isolated `quay.io/keycloak/keycloak:26.7.0` container was started with
`--features=scim-api`. The `scim-spike` realm enabled `scimApiEnabled`; a
confidential service-account client received `realm-management/manage-users`
and an Audience mapper for the realm SCIM base URL.

Observed results:

- a service-account token with the exact SCIM audience could create a User;
- the SCIM resource `id` exactly matched the Keycloak User ID;
- case-insensitive PATCH `Replace active=false` returned and persisted
  `active=false`;
- DELETE returned success and the following GET returned `404`;
- setting realm `scimApiEnabled=false` changed discovery from authenticated
  route behavior to `404`; reenabling it restored `401` for a missing token;
- the default realm User Profile rejected a missing family name and missing
  email explicitly, confirming that Keycloak profile configuration changes the
  accepted SCIM contract.

The live probe used synthetic `example.invalid` data and temporary credentials,
then removed its disposable container. It did not connect to an upstream IdP,
so it does not satisfy the broker-correlation gate below.

## Correlation Claim Finding

Supported OIDC protocol mappers can emit administrator-controlled hardcoded
claims for `orgmemory_directory_id` and `orgmemory_idp_alias`. A User Attribute
mapper can emit `orgmemory_workforce_id`, but the value is only trustworthy
after a concrete upstream broker mapper and User Profile permissions prove that
ordinary users cannot modify it.

F1 therefore does not claim a completed broker correlation. U0 is explicitly
blocked from enabling a provisioning connection until a live upstream IdP
probe records:

1. the immutable upstream object ID;
2. the broker mapper that stores it in a non-user-editable attribute;
3. the OIDC mapper output and redacted configuration fingerprint;
4. the Keycloak SCIM User ID, Keycloak OIDC `sub`, issuer, audience and `azp`;
5. deactivate, delete/recreate, and mapper-change results.

If that probe requires a custom Keycloak SPI, a separate accepted deployment
and upgrade decision is mandatory. The native SCIM ledger and invitation guard
remain disabled until then.

## Operational Scope

The alternative spike deliberately stops at protocol and configuration
evidence. A previous-version database upgrade rehearsal is not a foundation PR
gate for a rejected preview path. The production OrgMemory path still requires
its own additive migrations, disabled-by-default connection state, and
non-SCIM recovery administrator.

## Primary Sources

- [Keycloak SCIM administration guide](https://www.keycloak.org/docs/26.7.0/server_admin/#_managing_scim)
- [Keycloak feature support levels](https://www.keycloak.org/server/features)
- [Keycloak protocol mappers](https://www.keycloak.org/docs/26.7.0/server_admin/#_protocol-mappers)
