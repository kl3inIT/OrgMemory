# 0034 — Use keyset source listing with a status envelope

Status: accepted
Date: 2026-08-06

## Context

The Documents surface previously loaded every visible source and filtered the
array in the browser. Paging that result with an offset would be unstable while
ingestion changes the displayed revision update time, and status badges derived
from one page would misrepresent the whole result. The existing
`AssetSummaryPage` envelope uses offset page numbers for a comparatively stable
catalog, so copying it would preserve its shape while violating the Documents
ordering contract.

Source visibility also has two legs: content visibility supplied through the
secure retrieval port and source ownership. The ownership leg can include a
large connector corpus and must not bypass the same authorized-object ceiling.

## Decision

`GET /api/sources` returns an endpoint-specific envelope with `items`, an opaque
`nextCursor`, clamped `pageSize`, `total`, and whole-result `statusCounts`.
Listing orders the latest revision by `updated_at DESC` and Source Object id
ascending, and the exclusive cursor carries that same pair. The repository
over-fetches one row to determine whether another cursor exists.

Filters are applied only after resolving the bounded union of permission-visible
and owner-visible Source Object ids. Both legs and their union share the secure
retrieval authorization ceiling. An authorization outage or oversized union
fails unavailable; it is not represented as an authoritative empty page.

The envelope deliberately diverges from `AssetSummaryPage`: mutable ingestion
state requires keyset navigation, and status tabs require counts over the full
authorized-and-filtered result rather than page numbers and total pages.

## Consequences

- Rows remain stable across page transitions when other revisions change.
- Staged and failed latest revisions remain listable before publication.
- Status badges and processing polling remain truthful away from the first page.
- Clients must treat cursors as opaque and restart from the first page when a
  filter changes.
- Counts are advisory because authorization resolution and the database read do
  not share one transaction.

## Rejected alternatives

- Offset pagination and the `AssetSummaryPage` shape were rejected because an
  ingestion update can shift rows between offsets and cause duplicates or
  omissions.
- Pushing filters into the secure retrieval predicate was rejected because it
  crosses the Source Ledger boundary and cannot express the feature: retrieval
  admits only fully published READY evidence, so Processing and Needs attention
  would be structurally empty. Filters therefore remain an intersection with
  the separately resolved authorized Source Object set.
