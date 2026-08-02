# Independent review B

Reviewer: fresh Codex `gpt-5.6-sol ultra` session through Orca orchestration
Result: reject as written

## Verdict

The point-in-time `preparePublication` lease check is not a fence for later
staging/head mutation. One deterministic batch id derived from namespace and
logical idempotency contradicts the existing allowed retry after `ABORTED` and
cannot rebase a concurrent loser. OpenSearch's full record equality incorrectly
includes `createdAt`; its initial replay bypasses `COMMITTING` repair; and the
core can discard after abort failed to establish unreachability.

## Binding contract changes

- add a never-reset graph-job claim epoch;
- after verified preparations and a fresh job/current-target check, mint a
  durable exact commit permit bound to job, epoch, manifest, operation, attempt,
  predecessor, and target;
- transfer finalize-only authority from the expiring lease to that permit, so
  a crash after head CAS can still repair the exact winner without reopening
  general mutation rights;
- separate stable logical operation key from predecessor-bound physical attempt
  id;
- keep checkpoint state authoritative only for recovery while the namespace
  head remains the sole visibility authority;
- reconcile exact current or historical winners monotonically and never move
  `COMMITTING` backward to `PREPARING`;
- issue discard only through `abortIfUnreachable` returning an explicit discard
  permit;
- use delegate-then-`Error` crash tests after receipt, permit, history, head,
  marker, cache, and job boundaries, plus identical-worker-id stale-claim and
  concurrent-loser rebase tests.

If OpenSearch cannot validate an equivalent permit/fence at its head CAS, it
must use the PostgreSQL publication authority or the stale-worker guarantee
must be explicitly weakened. LightRAG remains only a callback-driven
process-local flush comparison, not a durable fenced ledger.
