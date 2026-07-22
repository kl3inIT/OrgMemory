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

- [ ] `ConnectorIngestionService.ingest(batch)`: validate tenant/space/versions;
  upsert `SourcePrincipal`s and run the matcher; build ACL entries + sealed
  membership from the permissions payload; register the raw source and seal a
  new ACL generation; compare-and-set the source ACL head.
- [ ] A `SLACK` source-system profile mapping channel→`SOURCE_GROUP`,
  member→`SOURCE_USER`, public-channel→workspace group, private→member list.
- [ ] Idempotency: a batch cursor + content-revision hash make re-crawl append
  only a new ACL generation when membership changed and skip the content path
  when the content hash is unchanged.
- [ ] Tombstones retire `SourceObject`s removed at the source (out of retrieval,
  evidence retained). Per-item failures are isolated and recorded, not fatal to
  the batch.
- [ ] Fail closed: unmapped principals grant nothing; no admin path can broaden
  a sealed generation; every ingest/rotate/retire appends a permission audit.

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
