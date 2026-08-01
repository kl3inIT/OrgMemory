# Independent architecture verdict

Date: 2026-08-02
Reviewers: two fresh Codex `gpt-5.6-sol ultra` advocates and one fresh blind
Codex `gpt-5.6-sol ultra` judge through Orca orchestration
Result: reject as written; accept the revised command/outcome contract below

The two independent advocates both rejected the draft. The blind judge read
their complete records, independently checked the decisive repository evidence,
rejected an always-PostgreSQL publication head as unnecessary, and made the
following seven conditions binding on implementation.

## Verdict

Retain the goal but replace the public raw-checkpoint proposal. The minimum safe
design is not an adapter-only replay fix, not distributed 2PC, and not an
always-PostgreSQL publication ledger. PostgreSQL remains canonical for the
source job and commit permit. The selected PostgreSQL or OpenSearch projection
adapter remains owner of its one visibility head.

## Strongest counterargument

This increment expands from two obvious retry bugs into producer fencing,
physical attempt identity, non-destructive cleanup authority, post-head
convergence, and copy-forward fencing. That is materially larger than a replay
patch. The expansion is nevertheless required because the current broad
exception handler can delete a batch after it already became visible, and a
point-in-time lease check cannot uphold the existing stale/cancelled-worker
promise.

## Binding must-fix list

1. **Critical — eliminate destructive cleanup after ambiguous outcomes.** A
   publish may have committed in PostgreSQL or won the OpenSearch head before
   the caller observes failure. No automatic abort/discard is allowed. Deletion
   requires a store-issued permit proving the exact attempt can never be current
   or historical; ambiguity keeps staging.
2. **Critical — fence head advancement with durable exact authority.** Add a
   never-reused graph-job claim epoch. After all preparations, issue or load an
   irrevocable commit permit in PostgreSQL after atomically rechecking claim
   epoch, lease, cancellation, current target, manifest, and attempt. Permit
   issuance is the authorization linearization point. The selected store binds
   the exact permit to `COMMITTING` and head CAS; no fresh cross-database lease
   read is required during OpenSearch CAS.
3. **Critical — split logical operation identity from predecessor-bound
   attempt identity and register before staging.** The stable operation pins
   namespace, producer key, manifest, and projection set. A physical attempt
   additionally pins the full predecessor identity and target generation.
   `createdAt` is metadata. A safely lost concurrent attempt can rebase without
   allowing changed content under the same operation key.
4. **Critical — make OpenSearch recovery monotonic and outcome-based.** Never
   return an exact replay before marker repair and never move `COMMITTING` back
   to `PREPARING`. Exact current/history wins finalize `PUBLISHED`; exact
   predecessor resumes; foreign or contradictory evidence follows the recovery
   matrix and never deletes on ambiguity.
5. **Critical — repair crash recovery inside copy-forward preparation.** Keep
   the prior rule that live `COPYING` is never stolen, but add a higher claim-
   epoch staging run/write fence so an abandoned run cannot wedge recovery and
   lower-epoch writes or cleanup cannot affect the selected run.
6. **High — converge post-head effects.** A durable publication proof grants
   exact cache invalidation and job-finalization authority after lease expiry.
   Replay invalidates both caches and completes idempotently. Correct the stale
   architecture claim that publication and job outcome already share one
   PostgreSQL transaction.
7. **High — prove every durable boundary without production-only crash flags.**
   Use ordinary operation decorators that delegate the real durable write and
   then throw a dedicated `Error`; recreate the store/lifecycle and cover stage,
   receipt, permit, history, head, marker, cache, job, stale same-worker claim,
   changed manifest, concurrent loser/rebase, and copy-forward death.

## Final contract

```text
PublicationIntent(namespace, operationKey, manifest, requiredProjections)
PublicationAttempt(attemptId, intent, exactPredecessor, targetGeneration)

beginOrResume(intent)
  -> Prepare(attempt)
   | Finalize(attempt, storedPermit)
   | Published(snapshot, proof)
   | RebaseRequired(head)
   | Rejected | Conflict | RetryKeepingStaging

commitOrReconcile(attempt, permit)
  -> Published(snapshot, proof)
   | RebaseRequired(head, optionalDiscardPermit)
   | RetryKeepingStaging | Conflict

abortIfUnreachable(attempt)
  -> DiscardAllowed(discardPermit) | KeepStaging | Published(snapshot, proof)
```

Adapter state is internal and monotonic:

```text
ABSENT -> PREPARING -> COMMITTING -> PUBLISHED
                 \---------------> ABORTED_SAFE
```

Only a store-proven unreachable `PREPARING` or `COMMITTING` attempt may become
`ABORTED_SAFE`. `PUBLISHED` and `ABORTED_SAFE` are terminal. The namespace head
alone controls reader visibility. Core reruns every exact idempotent preparation
in `PREPARING`; a bare projection-kind receipt never permits skipping.

## Recovery matrix

| State | Exact predecessor | Exact current/history winner | Foreign head | Missing or contradictory evidence |
| --- | --- | --- | --- | --- |
| absent | create preparing | corruption, fail closed | require rebase and fresh begin | create nothing, fail closed |
| preparing | rerun all preparation, issue/load permit | contradiction, fail closed | abort-safe only after proof, then rebase | keep staging, fail closed |
| committing | resume history/head CAS with stored permit | finalize published and replay proof | abort-safe only after exact-history exclusion | keep staging, fail closed |
| published | contradiction | exact replay | replay only from exact immutable history | corruption, fail closed |
| aborted-safe | refuse publish; permitted cleanup may repeat | contradiction, forbid deletion | logical operation may rebase | refuse attempt |

## Adjudication and rejected alternatives

- Reject the draft raw checkpoint DTO: it exposes adapter internals as a second
  apparent authority and encourages unsafe receipt skipping.
- Reject the minimal OpenSearch replay patch: it leaves destructive ambiguity,
  stale-worker mutation, rebase, copy-forward death, and post-head convergence
  unsolved.
- Reject always-PostgreSQL head ownership as required architecture. ADR 0011
  leaves production OpenSearch/Neo4j selection open, ADR 0014 keeps the storage
  boundary replaceable, and current auto-configuration deliberately lets the
  enabled OpenSearch adapter own the publication port. PostgreSQL is the safe
  fallback if OpenSearch cannot pass the permit/CAS and copy-fence conformance.
- Reject distributed transaction/2PC. Durable idempotent staging, one
  irrevocable exact permit, one adapter-local head CAS, and replay are enough.

## LightRAG comparison

Pinned LightRAG v1.5.4 waits for all sibling flush callbacks and drops only
process-local pending buffers on abort. It supplies useful flush attribution
and quiesce-before-cleanup evidence, but no durable attempt identity, producer
fence, generation head, receipt proof, or restart reconciliation.

## Scope limits

No 2PC, separate Neo4j ledger, reader fallback, retention/sweeper, re-extraction
checkpoint, connector/Asset redesign, or LightRAG crash-safety claim. The copy-
forward change is limited to the fence needed for publication preparation
recovery; general staged-generation retention remains future work.
