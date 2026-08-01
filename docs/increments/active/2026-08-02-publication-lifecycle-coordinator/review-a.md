# Independent review A

Reviewer: fresh Codex `gpt-5.6-sol ultra` session through Orca orchestration
Result: reject as written

## Binding findings

1. **Critical: ambiguous publish can delete visible data.** The core lifecycle
   catches a broad runtime failure, attempts abort, suppresses an abort failure,
   and always discards. OpenSearch can throw while finalizing the marker after
   its head CAS; PostgreSQL can return an ambiguous transaction acknowledgement.
   Cleanup must require an affirmative store result that the attempt is
   unreachable. An unknown/failed abort keeps staging.
2. **Critical: logical operation and concrete attempt are different
   identities.** A stable namespace/producer operation pins manifest and
   projections. A concrete attempt additionally pins the exact predecessor and
   target generation. A concurrent winner requires a new predecessor-bound
   attempt; one deterministic batch id for the whole operation would wedge or
   mutate immutable identity.
3. **Critical: terminal abort plus automatic abort poisons retries.** A caught
   transient exception cannot terminally reject the logical operation. Terminal
   abort applies to a concrete attempt only, or rejection/cancellation is an
   explicit logical-operation outcome.
4. **Critical: lease check is not a fence at head linearization.** The worker's
   lease/manifest check transaction ends before staging and head CAS. Add a
   never-reset claim epoch and validate a durable commit permit/fence where the
   head advances. Prefer a canonical PostgreSQL authority if an equivalent
   OpenSearch CAS fence cannot be proven.
5. **Critical: arbitrary-instruction automatic recovery is incompatible with
   the copy-forward rule that a crashed `COPYING` marker is never stolen.** Add
   an order-safe fenced copy restart, or narrow the guarantee and surface the
   poisoned preparation for intervention.
6. **High: bare receipt kinds cannot justify skipping preparation.** Discard can
   remove staged data without its receipt. Recovery must rerun exact idempotent
   preparation, or the adapter must validate an opaque receipt against staged
   data.
7. **High: a raw checkpoint getter creates a second apparent authority.** Keep
   adapter state private and expose command outcomes. The head alone controls
   visibility. Exclude `createdAt` from immutable content identity because a
   recreated retry timestamp must not conflict.

## Recommended contract

```text
operation: ACTIVE -> PUBLISHED | REJECTED
attempt:   ABSENT -> PREPARING -> COMMITTING -> PUBLISHED
                                  \-> LOST / ABORTED_SAFE

beginOrResume(intent, predecessor, fence)
  -> Resume(attempt) | AlreadyPublished(snapshot) | RebaseRequired(head) | Conflict
prepare(attempt) -> adapter receipt
commitOrReconcile(attempt, fence)
  -> Published | RetryKeepingStaging | RebaseRequired | Conflict
abortIfUnreachable(attempt)
  -> SafeToDiscard | KeepStaging
```

The intent pins namespace, producer key, manifest, and projection set. The
attempt additionally pins predecessor batch/generation and target generation.
Exact preparations rerun on recovery. The worker repeats cache invalidation and
job completion after a published replay.

## Recovery matrix

| Attempt | Exact predecessor | Exact winning head/history | Foreign head | Missing/contradictory evidence |
| --- | --- | --- | --- | --- |
| absent | register and prepare | fail closed as contradiction | create rebased attempt after fence validation | fail closed |
| preparing | rerun every preparation, then commit | repair only after exact identity proof | mark lost only after unreachability proof, then rebase | keep staging, fail closed |
| committing | validate permit/fence and resume | finalize published | mark lost only after proving its CAS cannot win | keep staging, fail closed |
| published | contradiction | exact replay | exact historical replay only | fail closed as corruption |
| aborted-safe/lost | refuse attempt; cleanup may repeat | preserve head as contradiction | logical operation may rebase if not rejected | keep or repeat permitted cleanup only |

## Comparison and alternatives

LightRAG v1.5.4 waits for every sibling flush and drops process-local pending
buffers on abort. That supports complete flush/error attribution but not durable
restart, fencing, generation heads, or receipt safety.

The reviewer rejected a minimal adapter-only patch and distributed 2PC. It did
not reject an always-PostgreSQL ledger: ADR 0011 makes PostgreSQL the canonical
published-head/recovery authority, so that is the preferred smallest strong
design unless a real PostgreSQL-independent deployment requirement is proved.

The reviewer also found `ARCHITECTURE.md` stale: it says graph publication and
durable job outcome share one PostgreSQL transaction, while current worker code
publishes, invalidates caches, and calls job completion separately.
