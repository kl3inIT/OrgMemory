# Plan — Shared Connector Polling Driver

Design: [design.md](design.md). Binding verdict:
[challenge-verdict.md](challenge-verdict.md). Working branch:
`increment/connector-polling-driver` in
`D:/OrgMemory-worktrees/full-codebase-review`.

## Step 1 — Characterize immutable cursor bytes

On unchanged production code, add deterministic golden assertions for Slack,
Google Drive, and GitHub:

- one content crawl: outer, content, permission, and membership cursors;
- one permissions-only crawl: outer, permission, and membership cursors;
- the historical literal prefixes, especially Drive's `google-drive-` rather
  than its `google_drive` source id.

Commit the characterization separately before modifying production classes.

Gate: `\.\gradlew.bat :integrations:connectors:test` green.

## Step 2 — Implement the driver and delegate adapters

Add `PollingConnectorBatchSource<C>` with:

- final enabled-connection poll loop and failure isolation;
- typed `(organization, source system, connection key)` cache identity;
- per-poll credential resolve, SHA-256 credential revision, adapter-supplied
  client-config revision, atomic derived-client reuse/replacement, missing and
  disabled connection retirement;
- one `ContentCadence`, advanced only after an admitted content batch, plus
  retirement of both schedule and served-request state;
- one 50% mostly-failed admission policy and standardized `mostly_failed`
  failure activity;
- an adapter-supplied historical literal around the exact existing outer
  cursor material; and
- propagation of unknown runtime exceptions.

Slack, Drive, and GitHub extend the driver while retaining source API calls,
mapping, completeness evidence, component cursor material, and pass-specific
failure counting. Keep constructors and `batchFor` test seams compatible where
practical; fall back to composition if narrow protected hooks are insufficient.

GitHub counts a repository at most once when collaborator or content requests
throw. Do not count configured truncation, content skipped for an already
failed audience, or incomplete source fields. Split the old one-repository
incomplete test and add below/at/above-boundary coverage, including the
revocation-stall/no-batch outcome at threshold.

Add focused driver/cache tests for reuse, credential rotation, Drive
impersonation rotation, missing/disabled eviction, clean cadence recreation,
expired Drive token refresh, no secret/fingerprint diagnostics, and unknown
exception propagation. Re-run every golden cursor vector unchanged.

Gate: `\.\gradlew.bat :integrations:connectors:test` green.

## Step 3 — Release evidence and repository gates

- Add one `.tegami/` patch entry describing the shared polling driver,
  standardized failure visibility, cache lifecycle, and cursor-byte
  compatibility.
- Run mechanical secret and diff checks.
- Run applicable backend static analysis for edited Java; if the Jmix-aware
  IDE inspector is unavailable/not applicable, record Gradle compile/test and
  mechanical checks as the fallback.
- Run terminating full `\.\gradlew.bat --no-daemon test`.
- Merge current `origin/main`, resolve only in-scope conflicts, rerun the
  connector gate, and open the code PR.

## Step 4 — Code PR through merge

Drive CI and CodeRabbit to green, address actionable findings with focused
commits, merge with a merge commit (no squash/rebase), and verify the merge on
`origin/main`.

## Step 5 — Consolidation PR after code merge

From the merged `origin/main`:

- update the knowledge-ingestion spec and mirrored test document with the
  driver boundary, cache/cadence lifecycle, standardized `mostly_failed`
  activity, GitHub denominator, and revocation-stall tradeoff;
- refresh both documents' `Source:` and `Reconciled:` lines;
- add a decision entry recording the driver/adapter boundary, strongest
  counterargument, cache secret residency, failure admission, and historical
  cursor-prefix requirement;
- update durable current-state architecture only where it describes adapter
  polling/client lifecycle;
- move this increment directory to `docs/increments/completed` and mark the
  roadmap row shipped.

Open a separate documentation PR, drive it through CI/review, merge it with a
merge commit, and verify final `origin/main`.

## Step 6 — Handoff

Write `tmp/batch4-report.md` with commits, PRs, challenge outcome, cursor
vectors, gates, review findings, merge SHAs, remaining risks, and Northstar
checkpoint evidence.

## Out Of Scope

Contract-generated models, skill constraints, extraction parallelism, route
loaders, persisted digests/fingerprints, and changes to source mapping or
completeness semantics beyond the explicit GitHub admission correction.
