# Google Drive Binary Ingestion Plan

Execute the accepted [design](design.md) as small code PRs. The first production
support matrix is PDF and DOCX. Every slice preserves the existing inline-text
connector path and keeps identity enumeration, content processing, and
permission reconciliation separately truthful.

## Architecture Gate

- [x] Audit the current Drive query, content contract, connector reconciler,
  source-revision pipeline, parser support, ACL rotation, retrieval, and worker
  job.
- [x] Compare pinned Onyx revision
  `618b5031bf21463f44e3bed9eb9d5073b806fec0` for export/download dispatch,
  size limits, parsing, batching, metadata, filename synthesis, and failure
  behavior.
- [x] Record the exact proposed boundary and adversarial evidence in
  `challenge-brief.md`.
- [x] Obtain the independent cross-model `REVISE` verdict in
  `challenge-verdict.md` at OrgMemory commit
  `93a0bc5952d5ee8651cf4082f4b8788d2af21367`.
- [x] Incorporate all seven must-fixes: binary raw payload semantics, explicit
  job kind, MIME/suffix rules, enumeration/content completeness split, tracked
  transfer reservation, SHA-256 identity, and deliberate connector owner.

## Slice 1 — Generic Observation And Completeness Contracts

- [ ] Replace the text-only `ConnectorContentItem` body with a sealed content
  payload: inline text or binary source observation. Migrate Slack, GitHub,
  Google Drive, fixtures, and tests in one cutover; leave no old constructor or
  alias.
- [ ] Add source URI, display filename, canonical parser filename, declared MIME
  and size, modified time, Drive SHA-256 when present, provider revision/change
  hints, and provider retrieval locator to the binary observation. Keep
  source-specific optional metadata opaque.
- [ ] Define `ConnectorContentFetcher` in core and a per-source registry that
  refuses missing or duplicate source claims. The contract takes typed
  connection/object identity, byte cap, and caller-owned sink; it carries no
  credential, callback, stream, bytes, or temporary path durably.
- [ ] Make Drive MIME authoritative for `KnowledgeContentType` admission and
  synthesize canonical `.pdf`/`.docx` parser names when needed. Preserve the
  actual display name; Tika/parser verification remains authoritative for the
  bytes.
- [ ] Separate complete object-identity enumeration from content-capture and
  permission completeness. Define the exact pruning predicate before changing
  fetch behavior.
- [ ] Add typed per-file and aggregate per-crawl raw-byte settings. Keep raw
  binary bytes separate from the shipped retained-text budget.

## Slice 2 — Binary Raw Source And Tracked Transfer

- [ ] Add an API-owned Flyway migration for required raw payload kind, nullable
  `raw_content` only for `BINARY`, binary `payload_sha256` equal to evidence SHA,
  and the immutable blob association needed to enforce that invariant. Preserve
  existing non-null text rows and `ddl-auto=validate`.
- [ ] Add a binary-specific registration command and idempotency comparison;
  never pass null or pseudo-text through `RegisterRawSourceCommand`.
- [ ] Add the API-owned early `SourceRevision.rawSourceObjectId` binding and
  explicit `SourceIngestionJobKind` (`UPLOAD`, `CONNECTOR_BINARY`) migration.
  Existing jobs backfill to `UPLOAD`; all writes become explicit before the
  column becomes required.
- [ ] Add a connector-binary transfer reservation with deterministic staging key,
  source/object/provider observation identity, byte cap, state, and timestamps.
  Commit it before writing object bytes.
- [ ] Implement the connector staging use case: resolve sealed source ACL;
  reserve; stream to `ObjectStoragePort`; verify observed bytes and SHA-256;
  transactionally create/reuse `EvidenceBlob`, register binary raw source,
  bind/stage revision, enqueue one connector-binary job, and finalize the
  reservation.
- [ ] Add age-gated recovery that retries or deletes only stale unfinalized
  reservations after proving no evidence-blob reference. Referenced blobs and
  arbitrary storage keys are never sweep candidates; delete failure remains
  visible and retryable.
- [ ] Prove concurrent crawls, provider retry, process death before/after object
  write, and transaction rollback cannot duplicate publication or leave an
  untracked object.

## Slice 3 — Drive PDF And DOCX Transfer

- [ ] Expand `GoogleDriveApiClient.FILE_FIELDS` with `sha256Checksum`, fallback
  checksum/provenance fields, `version`, `headRevisionId`, and the minimum source
  metadata required by the accepted identity rule. Preserve paginated
  permission resolution and shared-drive flags.
- [ ] Implement close-safe streaming media download for stored files. Check
  declared size before the call, abort at the first byte over cap, and classify
  retryable provider/network failures without buffering the body.
- [ ] Replace the Drive-only textual query with MIME dispatch that keeps
  Docs/Sheets/Slides export and text files inline while emitting PDF/DOCX binary
  observations. Do not route binary data through `downloadText()`.
- [ ] Use Drive SHA-256 only as a pre-download signal and verify it against the
  storage result. MD5, size, version, head revision, and modified time remain
  provenance/change hints; absent SHA-256 causes a streamed digest, not trusted
  MD5 identity.
- [ ] Preserve `webViewLink`, actual display filename/MIME, canonical parser
  filename, modified time, checksums, revision hints, and observed size so
  citation/admin presentation no longer invents a Drive `connector://` URI.
- [ ] Keep unsupported files outside the advertised capability matrix. Once
  PDF/DOCX is admitted, transfer failure becomes a per-item content failure and
  does not withdraw otherwise complete identity enumeration.
- [ ] Explicitly preserve the existing Google-native/text export asymmetry for
  this pilot: its crawl-time export failures still withdraw completeness.

## Slice 4 — Worker Parse And Publication Reuse

- [ ] Branch `SourceIngestionProcessor` by persisted job kind. `UPLOAD` retains
  current registration and ACL construction. `CONNECTOR_BINARY` reloads the
  early-bound raw source and sealed source ACL and must never execute the upload
  registration, hard-coded source identity, or upload ACL branch.
- [ ] Parse connector evidence through `DocumentParser` using the canonical
  parser filename and pinned processing profile, then preserve headings,
  paragraphs, and tables through the existing canonical blocks, normalization,
  chunks, embedding, vector, graph, and publication paths.
- [ ] Make claim/retry safe across transfer-complete/process-death,
  parse-complete/process-death, and publication-complete/process-death windows.
- [ ] Keep stable quarantine/failure codes for encrypted/invalid documents,
  unsupported/mismatched media, integrity mismatch, and parser failure. Never
  turn a failed supported file into empty ready content.
- [ ] Preserve the connection's provisioning actor as accountable publication
  owner, prove the tuple does not bypass the live source ACL, and preserve the
  connector-source delete refusal.

## Slice 5 — ACL, Revision, Completeness, And Retirement

- [ ] Change prune authorization to complete object-identity enumeration, not
  successful binary materialization. Scope filters, truncation,
  `incompleteSearch`, unread metadata, and unread sharing still block prune.
- [ ] Prove a permission-only crawl rotates the live source ACL for ready,
  pending, or failed binary content without download or parse.
- [ ] Prove revocation denies immediately and cannot expose a stale initial ACL
  while async parse/publication is pending.
- [ ] Prove storage-computed SHA-256 plus source object is the materialization
  identity: unchanged bytes perform no new materialization, changed bytes create
  one revision, and the current head moves only after publication.
- [ ] Prove download/parse failure leaves the last ready revision governed by its
  current ACL, neither retires it nor claims verified empty content, and does not
  stall retirement of a different genuinely missing object.
- [ ] Prove raw-byte exhaustion stops new binary transfers but continues file
  enumeration, ACL observation, permission checkpointing, and honest component
  status.
- [ ] Prove retirement occurs only after complete enumeration and remains blocked
  by partial folder scope, truncation, incomplete search, or unread metadata/ACL.

## Slice 6 — Operator And Citation Presentation

- [ ] Present PDF/DOCX type, actual filename, media type, canonical Drive link,
  revision time, processing/reservation state, and stable failure reason in the
  existing connection/document detail surfaces. Reuse generated API contracts
  and current alert/status patterns.
- [ ] Keep “open source” authorization-checked and browser-safe. `webViewLink` is
  provenance, not evidence that OrgMemory may serve a file.
- [ ] Add concise configuration copy distinguishing retained parsed-text budget
  from raw binary transfer budget; keep units operator-readable.
- [ ] Preserve keyboard access, mobile layout, light/dark themes, and visible
  pending/error states; browser-verify the real routes.

## Focused Verification Matrix

- [ ] Connector unit tests: MIME dispatch, extensionless names, source metadata,
  shared-drive download, declared/streamed oversize, aggregate raw budget,
  401/403/404/429/5xx, retry/backoff, response close, and permission continuation.
- [ ] Core integration tests: binary raw invariants, transfer reservation and
  recovery, staging atomicity, early binding, job kind, duplicate revision,
  enumeration/content split, ACL rotation, revocation, retirement, accountable
  owner behavior, and tenant isolation.
- [ ] Worker integration tests with real fixtures: valid PDF, extensionless PDF,
  valid DOCX, extensionless DOCX, MIME mismatch, malformed/encrypted input,
  crash-window retry, canonical blocks/chunks, and exact publication identity.
- [ ] Regression tests: Slack, GitHub, Google-native/text Drive, manual upload,
  citation authorization, connector delete refusal, and shipped Drive hardening.
- [ ] Run IDE/static inspection on every edited backend Java file, then
  `./gradlew --no-daemon clean test` as the terminating JVM context gate.
- [ ] Run web lint, typecheck, unit tests, production build, and browser
  verification when presentation changes.

## Live Pilot Proof

- [ ] In a real Workspace test folder, ingest one PDF and one DOCX through the
  configured Drive credential and retain redacted IDs/timestamps only.
- [ ] Ask a realistic Tasco question requiring each document; verify retrieved
  content and canonical Drive citation link.
- [ ] Rename one file without an extension and prove it still parses by MIME.
- [ ] Edit one file and prove one new current SHA revision with no duplicate
  after a repeated crawl and worker restart.
- [ ] Revoke one of two users and prove immediate denial for that user while the
  allowed user still retrieves the same evidence without reparse.
- [ ] Delete one file, run an intentionally incomplete crawl that does not retire
  it, then a complete crawl that does—even while a different binary transfer is
  failed.
- [ ] Exercise declared oversize, streamed oversize or equivalent fixture,
  exhausted raw-byte budget, checksum mismatch, transient download failure,
  parse failure, and stale transfer recovery; show stable outcomes and continued
  permission progress.

## Consolidation And Exit

- [ ] Reconcile implemented facts into `ARCHITECTURE.md`,
  `docs/specs/domains/knowledge-ingestion.md`, its mirrored test matrix, and all
  affected connector/permission specs. Refresh every affected `Source:` and
  `Reconciled:` line.
- [ ] Record the accepted binary raw-source, registered fetcher, transfer
  reservation, job-kind, completeness, SHA identity, source-ACL, and accountable
  owner contracts in an append-only decision, including rejected synchronous
  parsing and blind upload reuse.
- [ ] Update public/admin Google Drive capability copy so PDF/DOCX support and
  every remaining limitation are explicit.
- [ ] Record focused and live evidence in `verification.md`, move the increment
  to `completed/`, and update the roadmap only after all non-deferred gates pass.

## Exit Criteria

The increment closes only when PDF and DOCX from Google Drive use tracked,
immutable evidence and the durable parser/publication pipeline end to end;
current source ACLs govern retrieval across pending, ready, failed, changed,
revoked, and retired states; failure and resource limits remain visible without
blocking permission reconciliation or unrelated retirement; and the two-user
live Workspace proof plus terminating clean test pass. XLSX/PPTX, OCR/images,
shortcuts, comments/labels, OAuth, and `changes.list` remain explicit follow-ons,
not implied support.
