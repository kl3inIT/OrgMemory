# Apache AGE Published-Batch Backfill Challenge Verdict

## Verdict

Rejected as proposed. Rebuilding exact published relational graph snapshots
into AGE is valid, but ordinary API startup is not the safe operational
boundary. The binding contract is an explicitly invoked one-shot mode in the
existing API artifact and image. Deployment quiesces the old worker before the
one-shot begins and starts API and worker services only after it exits
successfully.

## Binding Fixes

1. Preflight the complete retained published `GRAPH` candidate set before the
   first AGE mutation. Bound batch count, relational entity count, and relation
   contribution count; report the measured offending batch on failure.
2. Enumerate with stable keyset pagination by publication time and batch ID.
   Join the exact published batch and require `PUBLISHED` status, an exact
   publication identity, and a durable `GRAPH` receipt.
3. Reconstruct every `ProjectionBatch` field from the durable batch row. Do not
   synthesize predecessor identity, idempotency key, claim epoch, or timestamps.
4. Inspect marker state as `READY_EXACT`, `MISSING`, `DUPLICATE`, `MISMATCH`, or
   `AGE_UNAVAILABLE`. Only marker drift is repairable; infrastructure failure
   remains fatal.
5. Before rebuilding, prove every relation contribution resolves its canonical
   relation and both batch-local endpoints. After copying, verify exact AGE
   entity and edge counts, then write the ready marker last in the same
   transaction.
6. Repair every retained published graph snapshot, not only current namespace
   heads, because historical published snapshots remain addressable.
7. Deployment order is binding: quiesce the old worker, run the one-shot,
   require zero exit status, start API, then allow the worker to start. The
   normal API/worker path remains non-mutating.

## Strongest Counterargument

A property-gated startup runner appears smaller because the API already
constructs the AGE runtime and the new worker depends on API health. That does
not fence the previous worker, does not bound rows inside one batch, and turns
historical repair into ordinary API readiness and rollback behavior. A
one-shot Compose operation reuses the same image and code without those
ambiguities.

## Rejected Alternative

Republishing only current heads through the normal worker would reuse the
publication protocol, but repeats extraction and embedding work and leaves
retained historical snapshots unreadable. Relational fallback remains rejected
by decision 0030.

## Required Proof

- exact-marker skip and repair of absent graph, absent marker, duplicate marker,
  and mismatched marker;
- AGE unavailability remains distinct and fatal;
- all limits and relational completeness fail before mutation;
- exact post-copy counts and rollback after a mid-copy failure;
- stable multi-page enumeration covers every retained snapshot once;
- deployment test proves worker stop, one-shot success, API health, then worker
  start.

## Residual Risk

AGE property uniqueness is not database-enforced, privileged out-of-band
corruption remains possible, and large-but-permitted repairs can exceed the
deployment window. Production limits therefore need runtime sizing evidence.
Workers outside this Compose project require separate operational fencing.
