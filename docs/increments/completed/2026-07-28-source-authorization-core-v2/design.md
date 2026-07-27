# Source Authorization Core V2

## Outcome

OrgMemory stores source identities by stable native identifier and stores source
group membership independently from per-resource ACL snapshots. A successful
membership synchronization can therefore revoke or grant access without rotating
the document ACL, creating a content revision, or rebuilding embeddings.

This increment is one end-to-end pull request. It is not a migration program:
disposable POC connector data may be re-ingested against the new canonical
model. The implementation must not add dual-write, dual-read, or a long-lived
legacy compatibility path.

## Repository Evidence

- `SourcePrincipal` is already scoped by organization, source system, and source
  connection, but the canonical field is named `external_key`; Drive currently
  uses mutable email values for user and group keys.
- `ConnectorIdentityItem` combines identity observation with
  `memberExternalKeys`, so membership has no independent completeness contract.
- `source_acl_group_members` belongs to one `source_acl_snapshot_id`.
  `ConnectorReconciler` consequently copies the same channel/group membership
  into every document ACL generation.
- Connector ACL rotation deliberately bypasses no-op suppression because
  membership is not included in the ACL digest.
- Retrieval joins group members from the same document ACL snapshot. The admin
  view guesses a group generation by selecting the largest per-document ACL
  generation, which is not a canonical group head.
- Source ACL filtering already happens before ranking and citations are
  rechecked. OpenFGA remains the application-policy authority while source ACL
  remains a hard ceiling.

## Decision

### Canonical source identity

`SourcePrincipal` keeps its OrgMemory UUID, tenant/source/connection scope and
`USER | GROUP` kind. Its source-owned identity is an immutable
`nativePrincipalId`. Email, login and display name are mutable observations and
never identify a principal by themselves.

Only a source `USER` may have an active mapping to `AppUser`. A source `GROUP`
never maps directly to an application user, SCIM Directory Group, or OpenFGA
role.

### Independent membership snapshots

```text
SourcePrincipal(GROUP)
        |
        v
SourceGroupMembershipHead --atomic--> sealed COMPLETE snapshot
                                      |
                                      v
                              USER | GROUP members
```

A snapshot is immutable evidence. Its monotonically increasing generation and
SHA-256 digest support compare-and-set, audit, and incident analysis; they do
not create an event-sourcing API or temporal authorization feature.

Membership evidence has two capture states:

- `COMPLETE`: sealed and eligible to become the active head.
- `INCOMPLETE`: diagnostic evidence with a mandatory reason code; never active.

Technical `FAILED`, `REJECTED`, and `UNAVAILABLE` remain synchronization-attempt
outcomes. A failed attempt cannot manufacture an authoritative membership
snapshot.

The database permits typed `USER | GROUP` members. Recursive authorization is
enabled only if deterministic cycle, depth, and expansion bounds are enforced;
otherwise nested membership is rejected fail closed rather than advertised.

### Resource ACL

Resource ACL snapshots contain only normalized `ALLOW | DENY` grants to source
principals. They do not contain or hash expanded membership.

The effective source path is:

```text
AppUser
  -> active SourcePrincipal(USER) mapping
  -> active sealed COMPLETE membership snapshot
  -> SourcePrincipal(GROUP) resource grant
```

`DENY` wins. Unknown, incomplete, unmapped, cross-tenant, or unsealed evidence
grants nothing. Retrieval applies this before ranking and uses the same
canonical evidence during citation recheck.

### Connector contract

Identity observation and membership capture are independently versioned.
Slack emits stable user and channel IDs. Google Drive emits provider-native
permission/grantee IDs when present; email remains an alias. A Google group
whose membership cannot be authoritatively enumerated is incomplete and cannot
grant access.

## Strongest Counterargument

The existing per-document membership is simple and already proves Slack
revocation. Replacing it touches the connector contract, persistence, retrieval,
admin evidence, and tests at once. Keeping it until a measured scale problem
would minimize near-term risk.

That alternative is rejected because the current representation is not merely
slow: it has no canonical group generation, forces unrelated document ACL
rotation for a membership change, and cannot state membership completeness.
Those are authorization-correctness defects for a third connector, not optional
performance optimization.

## Rejected Alternatives

- **Universal IAM graph:** duplicates OpenFGA and source authorities and creates
  convergence ambiguity.
- **Expanded user ACL on every document:** loses group provenance and repeats
  membership across every resource.
- **Dual-write migration:** creates two authorities and reconciliation work for
  disposable POC data.
- **Exactly two retained snapshots:** retention is an operational policy;
  evidence referenced by audit cannot be deleted by a hard-coded count.
- **Connector-provided partial members treated as complete:** can bypass a
  group-based `DENY` and leak data.

## Architecture Review

Repository convention normally requires an independent Claude Fable 5 debate
for this authorization boundary. On 2026-07-28 the project owner explicitly
waived that step because the Claude account quota was exhausted. The decision
instead incorporates completed independent adversarial, industry-pattern, and
repository-model reviews. All three supported independent membership and stable
native principals; the strongest counterargument above is retained rather than
silently omitted.

## Exit Evidence

- Slack and Drive contract fixtures use stable native principal IDs and
  independent membership payloads.
- A complete membership snapshot atomically advances the group head; incomplete
  evidence does not.
- A member removal closes retrieval without changing the document ACL snapshot,
  content revision, chunks, or embeddings.
- Cross-tenant, unmapped, incomplete, nested-unsupported, and unsealed paths
  fail closed.
- Permission administration names the active membership snapshot and
  generation; retrieval audit continues to pin the decisive resource ACL
  snapshot. A combined per-decision evidence explanation belongs to the
  follow-up permission-observability increment rather than being implied here.
- Focused connector/core/API/worker tests and terminating full Gradle tests pass.
