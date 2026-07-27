# Directory Group Authorization Mapping

## Outcome

An organization administrator can explicitly map one immutable Directory Group
to one allowlisted OrgMemory authorization target, preview the effect, approve
the change, and observe durable OpenFGA convergence.

SCIM group names and membership remain directory evidence. Authorization exists
only because an administrator created a separate mapping policy.

## Dependency

The [SCIM Directory Groups](../2026-07-27-scim-directory-groups/plan.md)
increment must be complete and must have proven that Groups alone grant
nothing.

## Mapping Contract

A mapping selects:

- organization and provisioning connection from the authenticated admin;
- immutable Directory Group server ID;
- target kind;
- target object;
- one server-allowlisted relationship;
- lifecycle, effective date, approver, reason, and optimistic version.

Initial target kinds are:

- an organization role supported by the current OpenFGA model;
- a Knowledge Space relationship supported by the current OpenFGA model.

The API does not accept arbitrary OpenFGA object types or relation strings. It
does not accept a Source Group, source ACL principal, department, Java
`UserRole`, or a group `displayName` as an authorization target.

The administrator first requests an impact preview containing:

- current direct members;
- users who will gain, lose, or retain effective access;
- target resources and relation;
- current conflicting/manual relationships;
- users who will be temporarily fail-closed during a revocation;
- the recovery administrator and self-lockout warning;
- a digest that is bound to the subsequent mutation.

Approval fails if the group, membership version, target, or preview digest has
changed. Every create, change, disable, and delete produces an immutable audit
event.

Authorization to create a mapping is itself conjunctive:

- every mapping mutation requires organization `can_manage_members`;
- a Knowledge Space target additionally requires `can_manage_acl` on that
  exact Space;
- an organization-role target is restricted to the organization's supported
  member-management boundary;
- the server derives organization and target ownership before preview and again
  before approval.

Possessing a SCIM credential never authorizes a mapping. A foreign or
unmanageable target is reported as a generic unavailable resource.

After an administrator activates a mapping, the upstream directory and its
Groups-scoped credential can influence access by changing membership. The
approval therefore displays that trust transfer explicitly, requires a healthy
rotatable Groups credential, and enables mapping-specific membership-change and
mass-grant alerts. This is still bounded by the typed mapping and Source ACL;
the provisioning client cannot choose another relation or target.

## Persistence And Ownership

Add at least:

### `directory_authorization_mappings`

The desired mapping, immutable group identity, typed target, relation,
organization/connection composite ownership, lifecycle, versions, preview
digest, actor, reason, and timestamps.

### `directory_managed_assignments`

The exact OpenFGA tuples and their ownership:

- a group-projection owner for direct `group#member` tuples while at least one
  active mapping needs that Directory Group;
- a mapping owner for the typed group-to-target grant tuple.

This avoids deleting shared membership projection when one group has multiple
active mappings. The ledger distinguishes directory-managed tuples from manual
administrator tuples and source-owned evidence. Reconciliation may delete only
tuples that this ledger proves the relevant owner created and no remaining
owner needs. It records desired and applied OpenFGA authorization model IDs;
convergence is not complete until readback succeeds against the intended model.

### `identity_access_blocks`

One row per user and security-reducing operation/mapping version. Effective
readiness is true only when no unresolved blocker remains. Completing one
outbox item removes only its blocker and cannot reactivate a user while another
revocation is pending.

### `identity_authorization_outbox`

Versioned desired-state operations with idempotency key, mapping/group/user
versions, retry state, lease, next attempt, last sanitized error, and completion
time.

PostgreSQL is the durable desired-state and ownership ledger. OpenFGA is the
application authorization authority. The worker converges them; the HTTP
transaction never pretends both systems committed atomically.

## OpenFGA Projection

The current model already permits a `group#member` subject set on organization
and Knowledge Space relationships. For a mapped Directory Group, the worker
owns:

1. direct membership tuples from application users to
   `group:<directory-group-id>#member`;
2. one typed grant from the target relationship to that
   `group:<directory-group-id>#member` subject set.

No tuple is inferred from a name. No Source ACL tuple or sealed generation is
ever written.

Projection uses a provisioning-specific OpenFGA write port and scope. It does
not broaden the existing `AdministrativeTupleScope` or reuse
`RoleAdministrationService`, both of which are designed for interactive,
user-subject administration.

Directory projections use a reserved OpenFGA group object-ID namespace. Every
writer that can touch that namespace must pass through one PostgreSQL ownership
arbiter/reference ledger. A pre-existing or concurrently identical unmanaged
tuple is never silently claimed; the interleaving is resolved before a managed
owner may later delete it.

Every operation is compare-and-set against mapping, group, and membership
versions. A stale retry may observe the new state and become a no-op; it cannot
recreate a deleted membership or superseded grant.

## Security-First Ordering

Authorization-reducing changes fail closed before asynchronous convergence:

1. In one PostgreSQL transaction, record the new desired state, add a distinct
   access blocker for every affected active user, materialize effective access
   false, and enqueue the versioned revocation.
2. The worker removes directory-owned OpenFGA tuples and verifies the result.
3. It enqueues/applies any replacement relationship.
4. Only after required tuples match desired state/model does it resolve that
   operation's blockers.
5. Effective access is recomputed from directory, local, and readiness state.

This applies to membership removal, mapping removal, and mapping target change.
It may temporarily deny unrelated access for an affected user, but it never
leaves a known stale elevated relationship usable.

Authorization-increasing changes do not require global user suspension. New
access appears only after the worker has written and verified the new tuples.

SCIM `active=false` still denies immediately in PostgreSQL and does not wait for
this outbox. Reactivation remains fail-closed until all required mapped
assignments converge.

User and Group lifecycle transitions are explicit:

- User tombstone adds a blocker, removes that user's managed membership tuples,
  verifies revocation, then leaves the user inactive/tombstoned.
- Group tombstone transitions every active mapping to `REVOKING`, blocks
  affected users, removes grants and membership projection, and only then
  completes the tombstone.
- Disabling the Groups capability is rejected while an active mapping exists,
  unless the caller chooses the coordinated operation that performs the same
  fail-closed revoke first.
- `READ_ONLY` freezes new SCIM mutations. `SUSPENDED` denies every SCIM
  credential but does not skip already queued security revocations.

## Failure And Recovery

- OpenFGA outage leaves reducing changes safely inactive and increasing changes
  unapplied.
- Retry backoff is bounded and observable.
- A reconciliation scan compares desired mappings, managed assignments, and
  actual OpenFGA tuples.
- Repair may recreate or remove only directory-owned tuples.
- Suspending a mapping begins a fail-closed revocation; connection `READ_ONLY`
  prevents new writes and `SUSPENDED` denies all SCIM traffic.
- Manual administrator tuples are never deleted as drift. An exact tuple that
  pre-exists or races without managed ownership is recorded as external and is
  not claimed for later deletion.
- Source ACL remains a hard ceiling even after OpenFGA grants.

The administration UI exposes pending convergence, affected users, age, retry,
and a safe repair action. It does not offer an unsafe “mark successful” button.

## Access Explanation

Permission explanations identify the complete derivation:

```text
Directory Group membership
  -> administrator-approved mapping
  -> OpenFGA relationship
  -> application permission
  -> Source ACL ceiling
```

They distinguish a pending projection, an OpenFGA denial, and a Source ACL
denial. A Directory Group row by itself is never presented as an effective
grant.

## Rollback

Disable new mapping mutations, then let or force revocation outbox work to
converge. The managed-assignment ledger identifies only tuples owned by this
feature. A binary rollback retains mappings, assignments, outbox, audit, and
the effective-active compatibility latch.

Rollback is incomplete until:

- all directory-owned grants are revoked from current desired/ownership state;
- affected users have correct readiness;
- manual tuples remain intact;
- a reconciliation report is clean.

No down migration drops ownership evidence.
