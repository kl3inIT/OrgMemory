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

## Consolidation

- [x] `docs/specs/domains/knowledge-ingestion.md` carries the shared-drive rule,
  the four ways completeness is withdrawn, subtree folder scope, what the crawl
  cursor covers, and the retry policy.
- [x] `docs/tests/domains/knowledge-ingestion.md` lists one proof per defect and
  records that the shared-drive proof was verified by reverting the fix.
- [x] `ARCHITECTURE.md` states the ledger rule the shared-drive defect violated:
  an adapter that cannot establish an ACL omits the object rather than sending
  an empty grant list.

## Evidence

Findings and priority order come from the dual review recorded in
[design.md](design.md); the independent review report is untracked at
`tmp/codex-connector-review.md` (session artifact, not repository history).

Beyond the planned slice, `GoogleDriveDocumentTypes` declared `application/json`
and `application/xml` indexable while its Drive query asked for neither, so files
of those types were never returned and never mentioned by a crawl that went on to
claim it had enumerated everything. That is the deletion path the completeness
claim exists to guard, reached through a filter that looked harmless. Both types
are now requested, and a test pins the two halves to the same set.
