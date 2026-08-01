# 0028 — Cross-store publication uses exact durable permits and local heads

Status: accepted (2026-08-02)

## Decision

PostgreSQL remains authoritative for graph-index job claims and issues one
irrevocable commit permit for an exact physical publication attempt. A logical
operation pins namespace, producer idempotency key, manifest, and required
projection set. Its physical attempt also pins the exact predecessor batch,
target generation, and never-reused claim epoch.

Every projection stages and records its durable receipt before permit issuance.
The selected PostgreSQL or OpenSearch publication adapter copies the exact
permit into its attempt state and owns the single local visibility-head CAS.
Adapter state is monotonic: `PREPARING -> COMMITTING -> PUBLISHED`, with only a
store-proven unreachable attempt entering terminal safe abort. An exact
current/history winner repairs its marker before replay returns.

Ambiguous permit or head outcomes retain staging. Cleanup requires a
store-issued discard permit for the exact batch. For a proven concurrent loser,
the worker retires the PostgreSQL commit permit before reverse-order projection
cleanup and retry. Cache invalidation happens after durable publication proof
and before idempotent job completion, so replay converges after either boundary.

## Why

The former broad exception handler always aborted and discarded staging. A
PostgreSQL commit acknowledgement or OpenSearch head CAS can succeed before the
caller observes failure, so that cleanup could delete visible data. A lease
check immediately before staging also did not fence a stale same-worker process
from later publication, and a crashed copy-forward run could poison retries.

One durable claim epoch fences producers. One exact permit is the authorization
linearization point. One adapter-local CAS remains the visibility linearization
point. This preserves replaceable projection adapters without pretending their
writes participate in the graph-job transaction.

## Strongest counterargument

Always storing the publication head in PostgreSQL would reduce the OpenSearch
recovery matrix. It would also override the existing replaceable publication
port, make OpenSearch readers depend on a second store, and is unnecessary once
the adapter passes exact permit, history, CAS, and recovery conformance.

## Rejected alternatives

- Distributed transaction or 2PC across projection stores; durable idempotent
  staging, one exact permit, one local head CAS, and replay are sufficient.
- A raw public checkpoint DTO; adapter states remain internal command outcomes,
  not a second authority for callers.
- An adapter-only OpenSearch replay patch; it does not fence stale producers,
  permit ambiguity, concurrent rebase, cleanup, or post-head convergence.
- Always-PostgreSQL projection-head ownership; PostgreSQL remains the safe
  fallback but is not imposed on conforming adapters.

Independent challenge record:
`docs/increments/completed/2026-08-02-publication-lifecycle-coordinator/`.
