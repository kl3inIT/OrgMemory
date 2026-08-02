# Durable cross-store publication lifecycle

Date: 2026-08-02
Status: accepted with changes after independent challenge.

## Problem

OrgMemory stages content, lexical, vector, and graph projections behind one
generation head. PostgreSQL publishes its batch record, immutable snapshot, and
head in one transaction. OpenSearch uses separate batch, receipt, history, and
head documents with compare-and-set transitions. The shared core currently
coordinates only one in-process call: prepare every projection, record its
receipt, publish, and abort/discard on a caught exception.

That contract is safe for ordinary exceptions but is not a durable restart
protocol:

- the worker creates a random batch id on every retry, while PostgreSQL rejects
  a second non-aborted batch for the same idempotency key; an orphan
  `PREPARING` row can therefore wedge a valid retry;
- a process can stop after some durable receipts exist. The next attempt does
  not recover or deliberately replay that batch;
- OpenSearch can stop after the namespace head advances but before the batch
  marker changes from `COMMITTING` to `PUBLISHED`; its current replay shortcut
  returns the visible snapshot without repairing the marker;
- status and durable receipt observations are adapter-private, so the core
  lifecycle cannot express one restart rule and the conformance kit cannot
  prove it across stores.

The publication head remains the only query-visibility boundary. This
increment does not create a distributed transaction between PostgreSQL,
OpenSearch, and Neo4j. They are replaceable projection adapters; Neo4j does not
own a separate publication ledger.

## Proposal under challenge

### Stable publication identity

Derive the graph publication batch id deterministically from the canonical
projection namespace and the producer's stable idempotency key. A retry for the
same work therefore addresses the same durable batch. The complete immutable
batch identity remains namespace, expected generation, target generation,
idempotency key, manifest fingerprint, and required projection set. Reusing a
batch id or idempotency key for different content fails closed.

### Core-owned durable state machine

Add a shared checkpoint contract that exposes the durable state and receipts:

```text
ABSENT -> PREPARING -> COMMITTING -> PUBLISHED
                 \-> ABORTED
```

`PUBLISHED` and `ABORTED` are terminal. A checkpoint contains the exact
registered batch, state, durable prepared projection kinds, and the published
snapshot when applicable. `begin(candidate)` atomically creates an absent
batch or returns the exact matching durable batch; it never adopts different
content.

The core lifecycle begins or loads the checkpoint, returns an exact published
replay, refuses an aborted batch, skips only preparations backed by durable
receipts, performs the missing idempotent staging operations, records each
receipt after its staging write commits, and then asks the publication store to
advance/reconcile the head. A caught pre-publication failure still aborts and
discards every preparation invoked by that process. Process termination leaves
`PREPARING` or `COMMITTING` durable for a later retry.

### Store-specific reconciliation behind one contract

PostgreSQL keeps publication atomic in one transaction. Its restart path loads
the same stable batch and receipts; no new schema or distributed transaction is
needed.

OpenSearch treats `COMMITTING` as a recovery state, not as a permanent lock:

- exact head already names the batch and content: finalize the marker as
  `PUBLISHED` and return the head;
- head still equals the expected previous generation: resume the same head
  compare-and-set, then finalize;
- head identifies another generation/batch: fail closed and never discard data
  that the current head could reference;
- observations are unavailable or contradictory: fail closed without changing
  the head or deleting staged data.

The OpenSearch replay path must reconcile the batch marker before returning.
Finalization uses retrying compare-and-set and remains safe to repeat.

### Worker fencing and completion

The durable graph job lease remains the producer fence. Immediately before
entering the publication lifecycle, the worker calls
`GraphIndexingCoordinator.preparePublication`, which rechecks job ownership,
binds the exact manifest, and renews the lease. A successful durable published
replay performs the same recheck before invalidating caches and completing the
job. The publication store never turns a stale or mismatched graph job into an
authorized producer.

## Strongest counterargument

The smallest repair is to make the worker batch id deterministic and move the
OpenSearch replay check after marker reconciliation. PostgreSQL already has an
atomic publish transaction, and all projection staging writes are generation
scoped and idempotent. A public checkpoint API may therefore expose adapter
internals, increase every fake/store implementation, and create a second state
authority beside the namespace head without improving visibility safety.

## Repository evidence

- `components/graph-rag-core/.../ProjectionBatchLifecycle.java` owns only one
  synchronous try/catch and has no restart observation.
- `apps/worker/.../GraphPublicationCommitter.java` creates `UUID.randomUUID()`
  for each attempt and completes the graph job after publication in a separate
  coordinator call.
- `core/src/main/resources/db/migration/V1__baseline.sql` has a partial unique
  index preventing two non-aborted PostgreSQL batches for one namespace and
  idempotency key.
- `integrations/graph-rag-postgres/.../PostgresProjectionPublicationStore.java`
  publishes the snapshot, head, and `PUBLISHED` status in one transaction.
- `integrations/graph-rag-opensearch/.../OpenSearchProjectionPublicationStore.java`
  writes a `COMMITTING` marker, then history/head, then `PUBLISHED`; its initial
  replay return bypasses marker repair.
- the publication conformance kit proves ordinary publish, conflict, replay,
  and abort behavior but not process-restart recovery.

## Pinned LightRAG comparison

LightRAG v1.5.4 at
`D:/OrgMemory/tmp/upstream-lightrag-v1.5.4` commit
`9a45b64c2ee25b1d806e90db926a8af37480bb16` flushes storage adapters through
`index_done_callback`, waits for every flush, and drops pending process-local
operations on abort. Its own storage contracts note that some OpenSearch
buffers are process-local until that callback. This is useful evidence for
complete flush/error attribution, but it is not a durable multi-process
publication ledger and cannot replace OrgMemory's generation head, receipts,
or restart reconciliation.

## Scope

- durable publication checkpoint/state contract in graph-rag core;
- stable graph batch identity and restart-aware lifecycle execution;
- PostgreSQL, OpenSearch, and in-memory conformance implementations;
- crash-window/recreated-store tests for partial preparation and committing
  reconciliation;
- worker tests for deterministic identity, fencing, replay, cache invalidation,
  and completion order;
- GraphRAG spec/test matrix, architecture, decision, and roadmap consolidation.

## Non-goals

- no two-phase commit or distributed transaction across canonical and derived
  stores;
- no new query visibility rule or reader fallback to an unpublished batch;
- no separate Neo4j publication authority;
- no background sweeper, retention policy, or staged-generation garbage
  collector;
- no re-extraction checkpointing, connector lifecycle change, or asset
  publication redesign;
- no claim that LightRAG callbacks provide crash-safe publication.

## Decision

The independent advocates and blind judge rejected the proposal under challenge.
`challenge-verdict.md` is binding. Implementation uses immutable logical intent
plus predecessor-bound physical attempts, command outcomes rather than a raw
checkpoint DTO, an irrevocable exact commit permit issued under a never-reused
claim epoch, monotonic adapter-local reconciliation, and store-proven discard
permits. PostgreSQL remains the canonical job/permit authority but is not forced
to own an enabled OpenSearch adapter's publication head. Preparation reruns
idempotently on recovery, including a claim-epoch-fenced copy-forward run; a
bare receipt is never trusted as proof that staging still exists.
