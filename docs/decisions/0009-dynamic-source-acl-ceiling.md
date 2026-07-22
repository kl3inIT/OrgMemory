# 0009 — Dynamic Source ACL Ceiling

## Status

Accepted on 2026-07-22. The enforcement change applies to live connector-backed
sources and lands with the first connector increment. Direct-upload behavior is
unchanged. Until that increment ships, the previous static-ceiling invariant
remains the tested behavior.

## Context

Sealed ACL snapshots, an append-only generation history, and compare-and-set
head rotation already exist. The current rule treats the ingestion-time snapshot
as an immutable ceiling: the current ACL head may narrow access but may never
grant more than the snapshot taken when evidence entered the system. The test
`widerCurrentAclCannotOverrideTheIngestionSnapshotDeny` proves this.

That rule is correct for one-shot native evidence but wrong for live sources
with dynamic membership. In Slack, a user added to a channel can read the full
channel history; in shared drives, a user granted a folder reads its existing
files. A static ingest-time ceiling makes OrgMemory deny users the source itself
allows, permanently, until re-ingestion. The system would be correct per its own
specification and wrong per every user's expectation of the source — an
undiagnosable class of complaint, hitting the first connector demo.

Connectors will re-crawl source permissions on a schedule or via webhooks. Each
crawl is new, verifiable ACL evidence.

## Decision

Split ceiling semantics by source kind:

- **Live sources (connector-backed).** The enforcement ceiling is the latest
  sealed permissions-crawl generation. Every permissions crawl appends a new
  sealed, immutable ACL generation; head rotation makes it current; retrieval
  enforces the current head within its freshness window. A newer generation may
  be wider or narrower than older ones — wider is legitimate exactly when the
  source itself widened access.
- **Native sources (direct upload, edge capture).** Unchanged. OrgMemory is the
  ACL authority through Knowledge Space, review, and publication. There is no
  external crawl and therefore no widening path.

The ingestion-time snapshot is retained permanently as audit evidence of what
the source allowed when evidence entered the system. For live sources it is no
longer an enforcement bound.

Invariants that replace the static ceiling for live sources:

- Within any single generation, entries are immutable and effective access
  cannot exceed that generation.
- A new generation may originate only from a verified permissions crawl or an
  explicit revocation event flowing through the ingestion pipeline. Sealed
  generations are never edited; administrators cannot widen access by hand.
- Unknown or unmapped external principals in a generation grant nothing.
- Effective access remains the intersection of tenant, latest source ACL
  generation, OpenFGA relationship policy, classification, and lifecycle. A
  wider source generation never bypasses OrgMemory policy gates.
- A current head older than its freshness window fails closed.

Revocation and new-member latency become a declared per-source property:
webhook-driven where the source supports it, otherwise bounded by the
permissions re-crawl cadence, with the head freshness window as the hard upper
bound. The declared latency is surfaced in source health rather than implied.

## Consequences

Head rotation, sealing, and freshness machinery are reused unchanged; only the
meaning of "ceiling" moves from first-ingest snapshot to latest sealed
generation for live sources. Retrieval audit records both the ingestion
snapshot ID and the enforced generation ID, so every decision stays explainable.

`widerCurrentAclCannotOverrideTheIngestionSnapshotDeny` is superseded for live
sources by per-generation tests: nothing exceeds its generation; generations are
produced only by the verified crawl pipeline; native sources keep the original
invariant.

Rejected alternative: keeping the static ceiling and re-ingesting content to
refresh permissions. Re-ingestion conflates permission change with content
change, breaks revision provenance, and still leaves a wrong-deny window equal
to the re-ingestion cadence.
