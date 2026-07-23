# Drive Connector Correctness Plan

One slice, adapter-only: nothing in `core`, the API, the schema or the web
client moves. Each item lands with its regression test; the build stays green.

## P0 fixes

- [x] API client hardening: request `permissionIds`, `driveId`, `size` and the
  top-level `incompleteSearch` field; return the listing with its
  incomplete-search flag; add `permissions.list`; retry rate limits honoring
  `Retry-After` plus bounded backoff for 5xx and transient I/O, with injectable
  waits; refuse response bodies over the hard cap.
- [x] Shared-drive ACLs: resolve absent inline permissions through
  `permissions.list`; a file whose sharing cannot be read is omitted from the
  payload entirely and the completeness claim is withdrawn. Proved by reverting
  the fallback and watching the regression fail.
- [x] `incompleteSearch=true` on any page withdraws the completeness claim.
- [x] Folder selection crawls the subtree: breadth-first folder expansion with
  cycle detection and a bounded folder count; files listed per parent chunk,
  deduplicated by id.
- [x] Canonical crawl cursor over sorted grants, identities with membership,
  content revisions and titles, and the completeness flag — a same-cardinality
  ACL swap must change it.
- [x] Oversize files: skip content above the metadata size bound and withdraw
  completeness; cap every response body read.
- [x] Content cadence advances only after a successful crawl.

## Gate

- [x] `.\gradlew.bat :integrations:connectors:test`
- [x] `.\gradlew.bat clean test` — green after merging `origin/main`, which this
  slice sits on top of.

## Evidence

Findings and priority order come from the dual review recorded in
[design.md](design.md); the independent review report is untracked at
`tmp/codex-connector-review.md` (session artifact, not repository history).
