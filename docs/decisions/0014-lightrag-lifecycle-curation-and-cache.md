# LightRAG Lifecycle, Curation And Cache

## Status

Accepted for PR 6 of the full LightRAG semantic-port program.

## Context

LightRAG `v1.5.4` supports document update/delete/rebuild, durable processing
status, retry, graph mutation, export, and exact model/query caches. Its
single-workspace implementation may rewrite graph identities in place, rebuild
shared identities from model-cache entries, and reuse query answers without an
authorization or publication-generation boundary.

Those observable capabilities must be preserved, but those implementation
choices cannot become authority in OrgMemory. Canonical source revisions,
evidence, ACL snapshots, publication state, and audit records remain the
security and recovery ledger. Graph rows and caches are derived projections.

This decision was challenged with Fable 5 against the pinned upstream
`v1.5.4` source and the current `light-rag` branch.

## Decision

### Document lifecycle

- An update creates a new immutable `SourceRevision` and Knowledge Asset
  version. Publication atomically changes the current version; it never edits
  old evidence.
- Delete revokes user visibility first by retiring the current version and
  archiving the Knowledge Asset. Derived graph contributions for that revision
  are then removed idempotently.
- Rebuild means reconstructing derived projections from the canonical ledger.
  It never depends on an extraction cache entry still existing.
- A leased indexing job supports durable cancellation, bounded retry, expired
  lease recovery, manual resume/rebuild, and a terminal superseded state.
- A worker must revalidate the current version and cancellation state before
  publication. A stale or cancelled worker cannot advance a visible head.
- Failed batches leave the previous head visible. Staged data is discarded or
  remains unreachable until cleanup.

### Graph curation

- Extracted evidence contributions are immutable.
- Create/edit operations append curated contributions with actor, policy,
  ACL-generation, reason, and time provenance.
- Merge is a reversible identity alias applied at read time. It does not delete
  source identities or combine descriptions globally.
- Delete is an identity suppression applied at read time. Evidence remains
  available to audit and may be restored by removing the suppression.
- Alias resolution rejects cycles. Relations that collapse to self-loops are
  excluded from the effective graph.
- Curation is organization/collection scoped, permission checked, audited, and
  invalidates the affected retrieval namespace.
- Effective descriptions, keywords, and weights are aggregated only after
  evidence authorization. A curation overlay cannot reveal or authorize
  underlying evidence.

### Export

- Export uses the same `AuthorizedEvidenceScope` as retrieval and contains only
  scope-visible entities, relations, evidence references, and provenance.
- Export is a bulk-egress operation with an explicit permission check and audit
  event.
- Core owns deterministic CSV, JSON, Markdown, and text formatting. Storage
  adapters own bounded, deterministic pagination.

### Cache

- Model-derived exact artifacts and permission-scoped retrieval results remain
  separate cache capabilities.
- Cache identity uses canonical length-delimited SHA-256 input, never a secret
  or API key.
- Keyword cache identity includes the normalized query, language, retrieval
  strategy, model route, and processing profile.
- Permission-scoped summary input includes the authorization fingerprint in its
  input hash.
- Retrieval-result identity includes the published snapshot, authorization
  fingerprint, query semantics, retrieval strategy, and model route.
- Retrieval cache hits remain derived data. Every returned citation is
  canonically rechecked before egress.
- Publication generation and ACL generation provide structural invalidation;
  namespace invalidation is also executed after publish, delete, abort, and
  curation. TTL limits orphaned rows.
- Streaming responses may read from a completed exact cache entry, but partial
  streams are never written.

## Boundaries

- `graph-rag-core` owns lifecycle state rules, curation semantics, cache-key
  canonicalization, invalidation orchestration, and export formatting without
  Spring dependencies.
- `graph-rag-testkit` owns reusable lifecycle/cache/curation conformance.
- Spring application services own authentication, authorization, transactions,
  audit, durable job leasing, and use-case orchestration.
- `graph-rag-postgres` is the first production adapter for curation, export,
  and caches. PostgreSQL is not embedded into the core contract.
- Full migration of graph publication into the shared
  `ProjectionNamespace`/`ProjectionKind.GRAPH` head remains PR 8; PR 6 binds
  graph idempotency to a canonical manifest without pretending that migration
  is already complete.

## Rejected alternatives

- Directly mutating extracted graph rows: destroys evidence lineage and can
  merge differently authorized text.
- Rebuilding from volatile LLM cache: cache eviction would become a correctness
  failure.
- Destructive entity merge: irreversible and unsafe across ACL boundaries.
- Query cache keyed only by query and mode: permits cross-tenant, cross-ACL,
  and stale-answer reuse.
- In-process cancellation or a single-writer flag: not restart-safe or
  multi-worker safe.
- Making PostgreSQL transactions or Spring types part of the reusable engine:
  prevents alternate adapters and obscures the semantic boundary.

## Consequences

OrgMemory intentionally differs from upstream storage mechanics while
preserving its allow-all observable behavior. Enterprise behavior is stricter:
revocation is immediate, evidence remains auditable, recovery does not depend
on cache residency, and no derived store can grant access.
