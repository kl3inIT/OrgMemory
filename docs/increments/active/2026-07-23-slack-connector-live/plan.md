# Slack Connector Live Plan

## 1 — Slack API Adapter

- [ ] `SlackConnectorBatchSource implements ConnectorBatchSource` using the Slack
  Web API client: content (`conversations.history`/`replies`), identity
  (`users.list`, `conversations.members`), permissions (channel visibility) →
  the versioned `content/v1` / `identity/v1` / `permissions/v1` payloads.
- [ ] Message-to-text and channel/member-to-identity rendering; a slim ID+ACL
  pull for cheap permission-only re-crawls.

## 2 — Credentials And Rate Limits

- [ ] Encrypted bot/OAuth token provider with a refresh lock; never logged;
  resolved per connection. Record only the managed secret location.
- [ ] `Retry-After` handling with bounded backoff; per-item `ConnectorFailure`
  isolation with a threshold abort.

## 3 — Durability

- [ ] Persisted per-connection crawl cursor (checkpoint/resume) replacing the
  staging in-process cursor; bounded per-batch retry.
- [ ] Deletion detection: diff indexed vs crawled set → emit tombstones.
- [ ] Connector content-edit re-materialization (new source revision on a changed
  content revision).

## 4 — Sandbox And Proofs

- [ ] Adapter unit/integration tests against recorded Slack API responses (no
  live network in CI).
- [ ] Documented Slack Developer sandbox run with SSO to local Keycloak: a real
  channel becomes retrievable only by mapped members; removing a member closes
  access on the next crawl.
- [ ] Regression across core, api, worker, and the OpenFGA model.

## Completion

- [ ] Update the knowledge-ingestion spec, ARCHITECTURE, and docs/tests with the
  live connector; move this increment to `completed`.
