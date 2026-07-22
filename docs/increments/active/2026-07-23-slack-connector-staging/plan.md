# Slack Connector Staging Plan

## 1 — Staging Contract

- [x] Define `contracts/connector/` versioned JSON schemas: a batch envelope
  (organization, source system, connection key, crawl cursor, per-payload
  versions) and three payload kinds — content, identity (users/groups/
  membership), permissions (per-object ACL). Not a Gradle module.
- [x] Add core records mirroring the contract (`ConnectorCrawlBatch`,
  `ConnectorContentItem`, `ConnectorIdentityItem`, `ConnectorPermissionItem`,
  `ConnectorTombstone`) with explicit payload-version fields; unknown version
  rejected (`ConnectorContractVersions` + `UnsupportedConnectorPayloadException`).

## 2 — Connector Ingestion Use Case (core)

- [x] `ConnectorIngestionService.ingest(batch)`: validate tenant/space/versions;
  upsert `SourcePrincipal`s and run the matcher; build ACL entries + sealed
  membership from the permissions payload; register the raw source and seal a
  new ACL generation; compare-and-set the source ACL head. Ledger mechanics reuse
  `KnowledgeIngestionService.registerConnectorSource` / `rotateConnectorAcl`
  (package-private, external principals allowed, membership sealed before seal);
  `ConnectorReconciler` runs each object in its own `TransactionTemplate` tx.
- [x] A `SLACK` source-system profile (`SlackConnectorProfile`) mapping
  channel→`SOURCE_GROUP`, member→`SOURCE_USER`, and fixing the OrgMemory
  classification (`INTERNAL`/`ALL_EMPLOYEES`) so channel membership is the binding
  gate.
- [x] Idempotency: a new object materializes once; a re-crawl converges the ACL
  by rotating the head (always a fresh generation, so membership-only changes take
  effect). A changed content revision rotates the ACL and defers re-materialization
  to the live increment (no stale broadening). Connector rotation never short-
  circuits on the entry hash (membership is not in it).
- [x] Tombstones retire `SourceObject`s removed at the source (out of retrieval,
  evidence retained). Per-item failures are isolated and recorded, not fatal to
  the batch.
- [x] Fail closed: unmapped principals grant nothing; the public upload path still
  rejects external principals; every materialize/rotate/retire appends a permission
  audit. (Proven end-to-end in Phase 4.)

## 3 — Worker Orchestration

- [ ] Worker reads a staging batch from a pluggable `ConnectorBatchSource`
  (fixture implementation now; the live Slack adapter implements the same port
  next increment), calls `ConnectorIngestionService`, then drives the existing
  parse/chunk/embed/publish pipeline for changed content.
- [ ] Durable, resumable batch processing with a persisted crawl cursor and
  bounded retry; observable per-object status.

## 4 — Fixture And Proofs

- [ ] Committed Slack-shaped fixture batches under `demo/fixtures/connector/`:
  an initial crawl (channel with member An) and a re-crawl (adds Chi, removes
  An), plus a tombstone case.
- [ ] Integration proof (Testcontainers): first crawl → An sees the doc, a
  non-member does not; re-crawl → Chi sees it and An is revoked, content not
  re-ingested (assert same revision, new ACL generation); tombstone removes the
  doc from retrieval; unmapped principal denied throughout.
- [ ] Regression: `:core:test`, `:apps:worker:test`, `:apps:api:test`, OpenFGA
  model tests.

## Completion

- [ ] Update the knowledge-ingestion spec and ARCHITECTURE with the shipped
  connector staging path; add a connector spec if the surface warrants one.
- [ ] Record evidence in docs/tests; move this increment to `completed`.
- [ ] Open the follow-up `slack-connector-live` increment for the real Slack
  Web API adapter, credential storage, rate limiting, checkpoint/resume, and the
  Developer sandbox run.

## Notes

- Onyx anchors for the live increment (not this one): `SlimConnector` for cheap
  ID+ACL pulls, `ConnectorFailure` per-item isolation with a threshold abort,
  checkpoint/resume, pruning as deletion detection, an encrypted credential
  provider with a refresh lock, and `Retry-After` rate limiting.
- This slice is fully testable without Slack; the live adapter is a drop-in
  `ConnectorBatchSource` producing identical batches.
