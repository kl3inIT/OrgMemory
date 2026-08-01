# Adversarial architecture challenge: durable publication lifecycle

You are an independent, read-only architecture reviewer. Attack the proposal;
do not validate it by default. Inspect the current repository and pinned
LightRAG source. Do not edit files, create commits, or mutate runtime state.

Read first:

- `AGENTS.md`
- relevant publication sections in `ARCHITECTURE.md`
- `docs/guidelines/agent-safety.md`
- `docs/guidelines/testing-harness.md`
- `docs/specs/domains/secure-graphrag.md`
- `docs/tests/domains/secure-graphrag.md`
- decisions `0008`, `0011`, `0014`, `0024`, and `0025`
- `docs/increments/completed/2026-08-02-publication-lifecycle-coordinator/design.md`
- current core lifecycle, worker committer, PostgreSQL publication store,
  OpenSearch publication store, in-memory store, and conformance tests

## Product promise at stake

A JVM or adapter process may terminate at any instruction boundary. A later
authorized worker must either complete the exact publication or fail closed;
it must never expose a partial generation, move the head backward, adopt
different content, steal live work, delete data referenced by the head, or
leave an otherwise recoverable idempotent job permanently wedged.

## Exact proposal under review

1. Give graph publications a deterministic batch id derived from canonical
   namespace plus stable producer idempotency key.
2. Add a core checkpoint with `PREPARING`, `COMMITTING`, `PUBLISHED`, and
   `ABORTED`, exact registered batch identity, durable receipts, and optional
   published snapshot.
3. Make the core lifecycle resume missing preparations from receipts and
   delegate atomic head advancement/reconciliation to each store.
4. Keep PostgreSQL publish in one transaction; make OpenSearch repair a
   `COMMITTING` marker when the exact head already won and resume CAS only when
   the head is still the expected predecessor.
5. Keep graph-job lease/manifest recheck immediately before lifecycle entry and
   before job completion on published replay.
6. Do not introduce 2PC, a second Neo4j ledger, a background sweeper, or a new
   query-visibility rule.

## Questions to decide

1. Is the public checkpoint API necessary, or is deterministic identity plus
   store-internal reconciliation sufficient and safer?
2. Does deterministic namespace/idempotency identity remain correct when the
   manifest or expected generation differs?
3. May the core safely skip a preparation solely because its durable receipt
   exists?
4. Which `COMMITTING` observations are provably recoverable, and which must
   fail closed?
5. Is the graph job lease recheck a sufficient stale-worker fence, including a
   pause after recheck but before head CAS? If not, what exact token must be
   bound and validated where?
6. When may abort/discard run without risking deletion of visible data?
7. What test seam can reproduce process termination after every durable write
   without production-only test hooks?

## Required verdict

Return plain Markdown with:

1. **Verdict**: accept, accept with changes, or reject.
2. **Strongest counterargument**.
3. **Must-fix list**, ordered by severity and binding on implementation.
4. **Repository evidence** for every finding, with exact file paths/lines.
5. **LightRAG evidence**, including why it is or is not a valid comparison.
6. **Recommended final state machine and API**.
7. **Recovery matrix** for absent/preparing/committing/published/aborted versus
   missing predecessor/exact predecessor/exact winning head/foreign head.
8. **Rejected alternatives**: minimal adapter-only patch, always-PostgreSQL
   ledger, and distributed transaction/2PC.
9. **Scope limits** that must remain non-goals.

Challenge your verdict against at least: termination after each preparation,
after each receipt, after history creation, after head CAS, after marker
finalization, after cache invalidation, and after graph-job completion; stale
worker lease expiry; same idempotency with a changed manifest; and concurrent
different publications for one namespace.
