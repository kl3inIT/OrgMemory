# SCIM Directory Groups

## Outcome

An enabled provisioning connection can mirror directory groups and their direct
user membership through SCIM without changing any OrgMemory authorization.

This increment makes directory evidence visible and manageable. It does not map
group names or membership to OpenFGA, application roles, departments,
Knowledge Spaces, or sealed Source Groups.

## Dependency

The [SCIM User Lifecycle Private Beta](../2026-07-27-scim-user-lifecycle/plan.md)
must be complete because every Group member references a stable SCIM User
resource in the same connection.

## Domain Meaning

The product uses several concepts that must remain distinct:

| Concept | Owner | Meaning |
| --- | --- | --- |
| OrgMemory department | Organization ledger | Application profile/structure |
| Directory group | Identity provisioning | Upstream workforce membership evidence |
| OpenFGA group subject-set projection | Authorization projection | Subject set used by an explicit policy |
| Source Group | Sealed source ACL | Mirrored source-document visibility |

A directory group's stable server ID is its identity. `displayName` is mutable
display data and can never select a role or relationship.

## Persistence

Add organization- and connection-owned tables:

### `scim_group_resources`

- stable server UUID;
- connection and organization composite ownership;
- connection-scoped `externalId`;
- normalized, connection-unique `displayName`;
- tombstone, internal version, and timestamps;
- no arbitrary extension payload.

### `scim_group_memberships`

- group, user resource, organization, and connection composite ownership;
- direct membership only;
- source version and audit timestamps;
- uniqueness for one direct membership;
- no OpenFGA tuple ID and no Source ACL generation reference.

Every foreign key proves that group and user belong to the same organization
and provisioning connection. A member value is the SCIM User server ID, not
`app_users.id`, email, username, or external ID.

Nested groups are unsupported initially because the current OpenFGA `group`
model accepts users and service accounts as members, not nested groups. Requests
that identify a group as a member fail explicitly; they are not flattened.

## Protocol Capability

The Group ResourceType remains hidden while only CRUD/search exists. After the
membership PUT/PATCH PR completes the full profile, an approved connection may
enable Groups and discovery advertises the Group ResourceType and schema.

Under `/scim/v2/Groups`:

- POST, GET by ID, list/filter, and `.search`;
- PUT replacement;
- atomic PATCH add, replace, and remove;
- DELETE tombstone;
- bounded pagination and `attributes`/`excludedAttributes`.

Interop includes:

- Entra's case-insensitive operation names;
- unique `displayName` within a provisioning connection;
- `excludedAttributes=members`;
- PATCH responses accepted as the exact status/body profile proven by fixtures;
- Okta `displayName eq` lookup;
- `members[value eq "<id>"]` removal;
- full membership replacement.

All requested members are validated before a mutation commits. A mixed valid
and invalid array fails atomically.

## No-Grant Invariant

At the end of this increment:

- no Group request calls `RoleAdministrationService`;
- no Group request writes OpenFGA;
- no Group request mutates `app_users.role`;
- no Group request creates or changes Source ACL rows;
- no group name is interpreted as `admin`, `member`, or a Knowledge Space role.

The UI labels these objects **Directory Groups** and states that membership
alone grants nothing. Authorization mappings remain unavailable until the
separate reviewed increment.

## Lifecycle And Rollback

Deleting a user transactionally marks every active direct Directory Group
membership removed, with removal version/time retained for audit; no membership
row is hard-deleted. The User and its memberships disappear from current SCIM
responses.

Deleting a group marks the group and every active membership tombstoned and
hides them from SCIM queries. In this increment it has no authorization cleanup
because no authorization projection exists. After mappings ship, group/user
tombstone and disabling Groups must first enter the mapping increment's
fail-closed revocation path.

Rollback disables the Groups capability while leaving Users active. Once
authorization mapping exists, this transition is unavailable while an active
mapping remains: the administrator must revoke and converge mappings first, or
use the coordinated fail-closed suspension operation. The tables and tombstones
remain, discovery stops advertising Groups, and no down migration is used.
