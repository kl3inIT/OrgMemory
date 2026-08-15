# Google Drive Ingestion Hardening Verification

## Delivered state

- Branch: `feat/google-drive-ingestion-hardening`
- Implementation commit: `9d7112d0`
- Scope: aggregate Drive text admission, permission-continuing budget behavior,
  benign permission-only reconciliation for an unmaterialized tail, descriptor
  UI exposure, and recorded-response permission-aware retrieval proof.
- Architecture challenge: Fable 5 returned `ACCEPT WITH BINDING CORRECTIONS`.
  The implementation applies the required 25 MiB floor, continues authorization
  observation after content admission closes, preserves permission-only
  reconciliation, and records the remaining exclusions.

## Backend evidence

All commands ran from the repository root on Windows through `cmd.exe`.

| Command | Result |
| --- | --- |
| `gradlew.bat :integrations:connectors:test --tests com.orgmemory.connectors.googledrive.GoogleDriveConnectorBatchSourceTests` | `BUILD SUCCESSFUL`; includes exact-boundary admission, crossing native exports, permission continuation, cadence, failure isolation, and status-sensitive content cursor coverage. |
| `gradlew.bat :core:test --tests com.orgmemory.core.knowledge.connector.ConnectorReconcilerTests` | `BUILD SUCCESSFUL`; an absent materialized source head is benign for permission-only reconciliation. |
| `gradlew.bat :apps:worker:test --tests com.orgmemory.connectors.googledrive.GoogleDriveIngestionIntegrationTests` | `BUILD SUCCESSFUL`; generated service-account key plus recorded responses traverse real PostgreSQL reconciliation and permission-aware retrieval, then a permission-only recrawl revokes without rematerialization. |
| `gradlew.bat :apps:worker:test --tests com.orgmemory.worker.connector.ConnectorCrawlCheckpointIntegrationTests` | `BUILD SUCCESSFUL`; the existing admin checkpoint projection exposes the aggregate-budget reason without replacing last-successful CONTENT state. |
| `gradlew.bat --no-daemon clean test` | Final post-rebase gate: `BUILD SUCCESSFUL in 15m 40s`; 105 actionable tasks: 65 executed, 40 from cache. |

No Java language server was attached: `lsp diagnostics` returned `No language
server found`. The fallback repository mechanical inspection reported no
package/path, migration-name, or edited-type structural defects; compilation,
narrow tests, and the clean context gate then passed.

## Frontend evidence

| Command | Result |
| --- | --- |
| `corepack pnpm --filter @orgmemory/web gen:api` | Passed; generated client refreshed. |
| `corepack pnpm --filter @orgmemory/web typecheck` | Passed. |
| `corepack pnpm --filter @orgmemory/web test:unit -- src/features/admin/connector-google-drive.test.ts` | Passed the full configured suite: 37 files, 135 tests. |
| `corepack pnpm --filter @orgmemory/web lint` | Passed. |
| `corepack pnpm --filter @orgmemory/web build` | Passed; Vite emitted only its existing large-chunk advisory. |
| `py -3 scripts/check_docs.py` | Passed: 577 Markdown files and 8 mirrored domain pairs. |
| `git diff --check` | Passed with no output. |

A real Vite browser surface at `/admin/connectors/google_drive?connection=workspace&step=configure`
was exercised with deterministic mocked API responses. The generic descriptor
wizard rendered `Retained text bytes per crawl` under Advanced, showed the
`67,108,864` default, disabled Save for `1,048,576`, and enabled Save at the
`26,214,400` minimum. This verifies the changed surface without introducing a
Drive-specific component.

## Review outcome

- The code simplifier found no safe simplification worth applying.
- The silent-failure review found no swallowed failure or unsafe fallback.
- The code review found one blocker: the content cursor originally omitted the
  aggregate-budget incomplete transition, allowing a prior complete batch with
  identical retained content to mask it. The cursor now includes
  `contentComplete=false`; the adapter and vertical worker tests passed again
  after the fix.

## Security and data handling

- Test credentials are generated in memory; no real Google credential exists in
  the repository.
- Recorded Drive responses are served by the test mock client; the proof makes no
  external network call.
- Direct-user retrieval is allowed only after the stable Drive principal maps to
  the application user. Revocation is proven through the same live ACL and
  permission-aware retrieval path.

## Known gaps and explicit exclusions

- No live customer or pilot Drive was crawled. That remains deployment evidence,
  not a correctness dependency for this bounded implementation.
- Google Directory group/domain expansion, delegated OAuth refresh-token
  storage, incremental Drive change tokens/webhooks, DLP/malware scanning, and
  large-tenant throughput remain out of scope.
- Browser verification used deterministic API interception rather than a live
  authenticated backend; authentication UI was not changed.
