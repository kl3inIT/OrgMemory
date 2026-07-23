# Drive Connector Correctness Design

## Why this increment

The Google Drive adapter shipped against recorded responses and a review of
Onyx's connector code. A second, adversarial review — this repository's analysis
cross-examined by an independent agent (Codex `gpt-5.6-sol`, dispatched through
Orca orchestration on 2026-07-23) reading the same two codebases — confirmed the
suspected operational gaps and found two correctness defects the first review
missed. Both reviews converged on one conclusion: the adapter has semantics bugs
that must be fixed before any abstraction work, because streaming a wrong crawl
only makes the wrong result faster.

This increment is the correctness patch. The abstraction moves it deliberately
defers — driver-owned crawl cadence, a typed crawl request, mid-crawl
checkpoints — are recorded at the end so the next increment starts from decided
ground.

## The defects, and what each fix must preserve

**Shared-drive files seal an empty ACL.** Drive does not populate inline
`permissions` (or `owners`) for items in shared drives; it returns only
`permissionIds`. The adapter asked for inline permissions alone, so a
shared-drive file mapped to zero grants, and the reconciler sealed that as a
complete generation granting nobody — fail-closed, silent, and wrong, with
`includeSharedDrives` defaulting to true. The fix requests `permissionIds` and
resolves absent inline permissions through `permissions.list`. When even that
fails, the file is left out of the payload entirely and the completeness claim
is withdrawn: the ledger keeps whatever was last sealed, because "could not be
read" must never be presented as "verified empty". This uses the omission idiom
the contract already has, rather than widening `ConnectorPermissionItem` with a
capture status; the contract question is deferred to the crawl-request rework.

**A same-size ACL change is invisible.** The crawl cursor hashed each object's
grant *count*. Replacing one reader with another kept the cursor identical, so
the driver's checkpoint deduplicated the batch and the revoked user kept
access. The cursor now hashes the full semantic payload — each object's sorted
grants (kind, key, gate), each identity with its sorted membership, content
revisions and titles, and the completeness flag — sorted before hashing so API
ordering cannot destabilize it. Deploying this changes every connection's
cursor once; the ledger ingest is idempotent, so the cost is one re-offered
batch per connection that reconciles to no change.

**An incomplete Google listing could authorize retirement.** With
`corpora=allDrives`, Drive may return `incompleteSearch=true`. The field was
neither requested nor read, so a listing Google itself calls incomplete kept
the completeness claim and let the ledger retire objects Google omitted. The
flag is now requested on every page and any page reporting it withdraws the
claim.

**A folder filter crawled direct children only.** `'X' in parents` matches one
level. An administrator selecting a folder means the subtree, so the adapter
now expands the folder set breadth-first — visited-set cycle detection, a
bounded folder count, and a withdrawal of completeness when the bound or an
unlistable folder truncates the walk — then lists files per chunk of parents.

**No retry.** One 429 failed the crawl. The client now retries rate limits
(429, and 403s whose reason is a rate limit) honoring `Retry-After`, retries
5xx and transient I/O drops with bounded backoff, and gives up after a bounded
number of attempts into the existing unavailable/failed-file paths. Waits are
injectable so tests never sleep.

**Unbounded downloads.** `readAllBytes` on an arbitrary file could exhaust the
worker. Files whose metadata `size` exceeds the content bound are skipped
without being indexed — and the completeness claim is withdrawn, deliberately
unlike the mime-type rule: a document's type is a property of the document, but
the size bound is this adapter's own policy, and crossing our policy must not
erase history the ledger already holds. Response bodies are additionally read
against a hard cap, so a lying or absent `size` still cannot exhaust memory.

**A failed content crawl advanced the content cadence.** The due time was
written before the crawl ran, so an API failure suppressed content until the
next interval and every intervening poll silently degraded to permissions-only.
The cadence now advances only after the crawl produced a batch. The residual
gap — ingestion failing downstream still advances it — is a driver concern and
is deferred with the cadence rework.

## Deferred deliberately

- Driver-owned cadence and a typed per-connection crawl request
  (`mode`, `modified window`, `resume token`), which also removes the adapter's
  in-memory due-time map and makes the permissions-only pass a contract.
- Mid-crawl checkpoints and paged streaming; separating the source resume token
  from the ingestion idempotency key.
- A capture status on `ConnectorPermissionItem` (`COMPLETE`/`UNAVAILABLE`).
- Group/domain membership through the Admin SDK; `permissions.list` fallback
  for Google groups' members.
- The `application/json`/`application/xml` mime types declared indexable but
  excluded by the Drive query — dead code either way, resolve with the next
  content-type decision.
- Verify whether unchanged ACL reconciliation appends a new sealed generation
  per crawl (write amplification) before deciding on an evidence-hash skip.
