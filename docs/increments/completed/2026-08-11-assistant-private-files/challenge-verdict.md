# Architecture Verdict: Assistant Private Files

Date: 2026-08-11

Review basis: `cec5ad4bd24c44a1bbb2c0a69c632ff387459c48`

Primary proposal base: `f8e30f0fac52ddba3ebcf1fc7ee79bbe91d5077f`

Counterpart: Claude Fable 5, one-round read-only review

Reference pins: Onyx `618b5031bf21463f44e3bed9eb9d5073b806fec0`,
Northstar `caef9a9a55e60e8bc99b47275b4840d6cd940372`

## Execution note

The first Orca terminal was interrupted by an Orca runtime restart and produced
no response. A replacement Orca terminal could not be created. The same single
brief was therefore executed once through the local Claude CLI at
`claude-fable-5`, with read-only tools and no follow-up. This was one debate
round, not a second architectural exchange.

## External verdict

Fable returned `REVISE` and ultimately selected the reusable,
organization-and-actor-private, retention-bounded `AssistantFile` over the
conversation-only alternative.

The strongest surviving case for conversation scope is that it fixes the
paperclip mismatch with a smaller discovery and lifecycle surface, stays closer
to OrgMemory's governed-Knowledge thesis, and is easier to widen later than a
personal file library is to retract. It loses because nearly all expensive
mechanics — private storage, worker processing, exact selection, private
citations, deletion, and retention — are required by both variants, while
conversation-only identity forces repeat upload, scan, parse, and embedding.
Mandatory bounded retention captures its main safety benefit without giving up
Recent Files.

## Committed recommendation

Ship a reusable `AssistantFile`, subject to every condition below. On 2026-08-11
the project owner explicitly waived a malware/DLP integration for this increment
after the pinned Onyx upload path was re-verified and found to have no antivirus,
ClamAV, malware, or DLP gate. Assistant admission, parser isolation, size/resource
limits, and the closed archive/image scope remain mandatory.

1. Private chunks use a distinct citation identity and owner-authorized
   hydration/download path. Expired or deleted evidence leaves an inert message
   marker and returns opaque not-found on open.
2. One turn uses one evidence lane in this delivery. Private and governed
   selections cannot be mixed.
3. `expires_at` is fixed at upload from server policy, has a hard finite ceiling,
   and does not renew on use. Expiry is visible before selection.
4. Claim locks or compare-and-sets lifecycle state. Delete denies new use before
   asynchronous cleanup; cleanup order is DB denial marker, private projection,
   then object bytes, with idempotent reconciliation for partial failures.
5. Server-side type detection and the channel's size/resource limits are
   mandatory. Malware/DLP is an explicitly waived non-goal for this increment;
   download serving is server-mediated with `nosniff`, sandboxing, and a closed
   inline allowlist.
6. Recent Files is actor-and-organization scoped, bounded, and paginated. Actor
   offboarding and organization deletion enter the cleanup lifecycle. Retained
   filename/media snapshots are bounded and sanitized.
7. Private projections and caches are partitioned by organization, owner, file,
   and processing generation. Architecture tests prove neither organizational
   retrieval nor graph projection can observe them.
8. The worker-private processor calls the existing package-private
   `DocumentProcessingEngine` with identical requested/resolved profile pinning;
   the API never parses.
9. Every download performs fresh owner authorization and streams through the
   storage port; the browser never receives a durable presigned object URL.
10. The lane has closed failure codes, cleanup and storage metrics, and negative
    owner/expiry/retry tests before the composer switch.

## Rejected alternative

A conversation-scoped, non-reusable ephemeral attachment remains defensible but
is rejected for this increment. It duplicates most processing and security cost,
causes repeated work for the same actor, and does not improve isolation once the
reusable lane has mandatory finite retention, no sharing, no library-wide
search, and owner-bound retrieval.

## Scope limits

No `Add from Knowledge`, in-place promotion, sharing, project association,
legal hold, images/OCR, archives, email recursion, provider-native files, TTL
renewal, mixed-lane turn, cross-actor deduplication identity, or migration of
existing governed bindings. `Publish to Knowledge` remains a separate Source
registration; its bridge mechanics require a later publication review.

Malware scanning and DLP remain future security capabilities rather than hidden
prerequisites of this lane. Reconsidering that waiver requires its own explicit
scope; it must not be silently inferred from parser success.
