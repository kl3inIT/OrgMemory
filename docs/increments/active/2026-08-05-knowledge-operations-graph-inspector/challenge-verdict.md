# Architecture Challenge Verdict - Manual Failed Upload Retry

Date: 2026-08-05  
Reviewed base: `876246da`  
Verdict: **reject the manual retry; continue the UI increment without it**

## Strongest Contradiction

A terminal `FAILED` job does not prove that no stale producer can still publish.
`SourceIngestionCoordinator.claimedJob(...)` checks only `PROCESSING` plus a
reusable worker id; the claim has no never-reused epoch and the lease need not
still be valid. The worker performs its last claim check before entering Asset
publication, whose activation and outbox work span `REQUIRES_NEW`
transactions. A lease can therefore expire and be reclaimed while an old
producer can still apply publication.

Resetting that same job from `FAILED` would introduce a new live cycle without
fencing the old producer. This is the failure class that decision 0028 already
requires graph publication to solve with exact durable commit permits.

The existing publication identity check is also insufficient for replay. It
does not pin parser, chunker, processing-profile digest, or deterministic chunk
manifest. A retry could compute new processing metadata while resolving an
already-applied publication containing chunks from the earlier attempt.

## Must Fix Before Retry Is Reconsidered

1. Add a never-reused claim epoch to Source Ingestion and carry worker id,
   exact epoch, and lease validity through every stage, failure, completion,
   and publication mutation.
2. Fence Asset activation with the exact current ingestion claim or a durable
   Source Ledger-owned publication permit. A pre-publication check is not
   sufficient.
3. Persist and compare the complete processing identity and deterministic
   output manifest across recovery.
4. Accept only exact `FAILED`, or add a durable idempotency key/manual-cycle
   marker; `PENDING` is ambiguous with automatic backoff.
5. Lock and revalidate source, revision, and job in one documented order before
   allowing a reset. Never reset `PROCESSING` work.
6. Keep original uploader plus current Knowledge Space `can_create_asset`, but
   translate every missing, denied, invalid-lifecycle, connector, archived, and
   cross-tenant case to the same opaque response.

## Committed Recommendation

This increment ships visible bounded failure details, desktop master-detail,
mobile Sheet, truthful access copy, corrected upload for `QUARANTINED`, and the
redesigned graph inspector. It exposes no retry endpoint or retry affordance.

Claim fencing and exact publication recovery become a separate architecture
increment. Only after that foundation is proven may a command reset the same
revision and unique job.

## Mandatory Future Tests

- Expired and reclaimed same-worker/different-worker claims cannot stage,
  fail, complete, or activate publication.
- Concurrent retry requests produce one transition and one worker cycle; a
  claim race never resets `PROCESSING` work.
- Recovery is proven before prepare, after pending preparation, after OpenFGA
  write, and after `APPLIED` but before Source READY.
- Recovery leaves exactly one asset, version, chunk set, tuple set, source
  head, and graph job, with processing/manifest drift pinned or rejected.
- Authorization and opacity cover owner, revoked owner, unrelated actor,
  administrator, cross-tenant actor, connector, archived, READY,
  QUARANTINED, and unavailable OpenFGA.

## Forbidden Scope

- Creator-authorized delete or cancellation of pre-publication sources.
- Retrying quarantined bytes unchanged.
- A second job or revision used to evade the unique lifecycle.
- Connector retry, arbitrary reindex, physical purge, weaker authorization,
  frontend authority, or raw internal exception text.

