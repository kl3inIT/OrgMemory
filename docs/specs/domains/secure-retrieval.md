# Secure Retrieval Spec

Source: `core/src/main/java/com/orgmemory/core/knowledge`,
`core/src/main/java/com/orgmemory/core/permission`,
`apps/api/src/main/java/com/orgmemory/api/knowledge`, and
`integrations/authorization-openfga`.

Reconciled: `2026-08-02-knowledge-space-audience-main-sync (pending merge commit)`.

## Current Behavior

Knowledge search evaluates tenant, the stable Knowledge Asset's current-version
pointer, active immutable version, current source revision, ingestion ACL,
current ACL head, applied publication/model/profile generation, OrgMemory
policy, and classification. OpenFGA `ListObjects` supplies candidate stable
asset IDs. SQL applies every canonical predicate before PostgreSQL FTS,
pgvector, or graph ranking and before model context assembly. OpenFGA
`BatchCheck` and canonical SQL rechecks guard selected evidence. Every serving
surface performs that batch recheck through one shared collaborator with a
mandatory typed result policy: hybrid search filters denied evidence and may
return a partial result, while citation opens and the GraphRAG final closure
require every decision allowed and fail the whole request otherwise. Each
surface keeps its own deny-reason and exception mapping. The resulting
evidence set is the immutable authorization snapshot for one Assistant turn.
Only evidence that fits the model-context budget is exposed as a citation, and
answer tokens stream without a post-generation authorization replay. A
revocation applies to every new turn; an already-started turn may finish under
its request snapshot and is bounded by the configured two-minute turn timeout.

The same canonical SQL intersects OpenFGA eligibility with the Space's
persisted audience mode. Organization mode admits organization members;
department mode requires the current persisted actor department to equal the
Space owner; restricted custom mode requires a matching user or department row
in the PostgreSQL audience ledger. OpenFGA remains necessary, but a stray tuple
that contradicts the persisted mode is never sufficient. This Space gate is
then intersected with Source ACL, classification, publication, lifecycle,
tenant, and evidence-integrity gates; no Space mode overrides them.

Parent Knowledge exposes the permission-aware query, immutable evidence,
secure result, and verified grounding through the exact `knowledge::search`
named interface. Assistant and Asset Registry cross that interface. API and
Worker inject Retrieval interfaces for engine selection, citation/source
opening, bounded single-Asset authorization inspection, and embedding-profile
resolution. Full evidence-scope resolution and the default/JDBC implementations
are package-private. The bounded inspector independently rechecks the Asset's
relationship decision and authorization-model identity before canonical SQL,
so it does not rely on a caller-supplied authorization precondition. The
closed Retrieval nested module retains authorization, ranking, and persistence
behind an exact outgoing dependency allowlist. Its entity, repository,
canonical store, resolved scope, candidate, catalog implementation, and
scope-unavailable exception are not part of the public module API.
Asset existence, active authorization-scope, and current catalog reads cross
one Asset-owned query that keeps tenant and lifecycle predicates behind the
closed Asset module; Retrieval imports neither Asset repository.
Retrieval also reloads the current active subject, department, and Executive
state through Organization-owned queries before resolving evidence or source
visibility. It does not trust those actor fields as authorization facts and
imports no Organization entity, role, or repository.

Graph exploration, export, and curation cross the Retrieval-owned
`GraphEvidenceVerifier` and immutable `VerifiedGraphEvidenceScope`. The
package-private implementation alone resolves canonical authorization state
and rechecks governing evidence through the secure retrieval store. Graph
imports neither the scope resolver, internal resolved scope, store, nor secure
candidate representation. Unknown Knowledge Spaces are rejected rather than
degrading to an empty/zero scope, and each governing-evidence recheck contains
only the Asset IDs authorized for the requested Space.

Citation URLs are opaque API routes, not object-storage URLs. Opening one reruns
the current canonical evidence boundary once, validates the revision and blob
integrity, and streams the original bytes through the authenticated API with
`no-store` and `nosniff`. Missing and denied citation reads return the same
generic `404`. A missing control-plane role or incomplete current actor is
rejected at the request boundary with `403`.

Source Ledger owns the tenant-scoped citation evidence query. It accepts the
permission-verified revision and Asset identities, requires a ready matching
revision plus a validated blob, and returns only immutable response and storage
integrity metadata. Retrieval imports no Source Revision/Evidence Blob entity,
repository, or lifecycle enum. Missing revision and unavailable blob outcomes
remain distinct audit reasons even though both map to the same opaque `404`.
An object-storage length or digest mismatch closes the stream, records a
`CITATION_BLOB_INTEGRITY_FAILED` deny audit, and returns unavailable without an
allow audit.

Control-plane roles (`ADMIN`, `REVIEWER`, `CONTRIBUTOR`, `VIEWER`) are separate
from knowledge roles (`EMPLOYEE`, `MANAGER`, `DIRECTOR`, `EXECUTIVE`). Admin does
not imply Executive or source access. Classification requires:

| Classification | Declared scope | Employee/Manager/Director | Executive |
| --- | --- | --- | --- |
| Public | All | allow | allow |
| Internal | All Employees | allow | allow |
| Confidential | Own Department | own department only | cross-department allow |
| Restricted | Executive Only | deny | allow |

Every classification decision is still intersected with tenant, both source ACL
snapshots, and OrgMemory policy.

The administrator's Knowledge Asset `can_view` inspector reuses the canonical
eligibility SQL for exactly one asset, after the OpenFGA relationship gate has
allowed it. It does not call `ListObjects`, enumerate the tenant's catalog, or
change enforcement. A missing canonical row is a content-policy denial; a
database failure is `UNKNOWN`. The diagnostic groups Source ACL,
classification, publication, lifecycle, and current-version eligibility into
one canonical gate because the shared SQL remains the authority and the
inspector does not maintain a second policy implementation.

ACL snapshots are immutable and sealed. A compare-and-set head selects the
current generation; superseded or absent current evidence fails closed. The
latest sealed `COMPLETE` source ACL remains authoritative after its freshness
timestamp; expiry is connector-health evidence rather than a universal
authorization denial, consistently in canonical retrieval and PostgreSQL
GraphRAG. Audit records include request/decision context and exact snapshot IDs;
raw query text is represented only by a hash.

OIDC identities resolve only through an explicit issuer/subject binding; email
and identity-provider roles never bootstrap access. Knowledge ACL principals
include namespaced OrgMemory users, departments, and organizations plus verified
external source users/groups resolved through the mapping ledger. The in-app
Assistant and read-only MCP tool use the same GraphRAG application service.
Graph-assisted retrieval, graph exploration, citation streaming, and export all
reuse the same authorized evidence scope; no graph node or relation owns an
independent ACL. Multi-source derived-permission intersection remains open.

## Source Modules

- `core.permission`
- `core.knowledge`
- `apps/api.knowledge`
- `apps/api.security`

## Related Decisions

- [0003](../../decisions/0003-postgresql-ledger-openfga-authorization.md)
- [0009](../../decisions/0009-dynamic-source-acl-ceiling.md)
- [0012](../../decisions/0012-stable-knowledge-assets-and-immutable-versions.md)
- [0029](../../decisions/0029-typed-knowledge-space-audiences.md)
