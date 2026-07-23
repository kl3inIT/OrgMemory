# Slack Connector Live Plan

Three phases. Phase A is core-side and needs no Slack credential, so it ships
first and the adapter lands against a stable core. Each item is a commit that
leaves the build green.

## Phase A — Core (no credentials)

- [ ] Consume `identity_trust`: resolve the batch's connection decision once per
  crawl and pass it into automatic matching, so `SSO_EMAIL_JOIN` fires when the
  source vouches for the principal *or* an administrator attested the connection.
- [ ] Content-edit re-materialization: a changed content revision appends a new
  source revision and re-runs normalize/publish instead of reporting
  `ROTATED_CONTENT_DEFERRED`.
- [ ] Persisted per-connection crawl checkpoint replacing the in-process cursor
  set in `ConnectorCrawlRunner`; bounded per-batch retry.
- [ ] Deletion detection: diff the indexed set against the crawled set and emit
  tombstones, gated on the batch declaring a complete crawl.

Gate: `.\gradlew.bat :core:test` and the worker connector slice, both proved
through the existing `FileConnectorBatchSource`.

## Phase B — Slack adapter

- [ ] New `integrations:connectors` module; `SlackConnectorBatchSource` in
  `com.orgmemory.connectors.slack` implementing `ConnectorBatchSource` over
  `conversations.list`/`history`/`replies`/`members` and `users.list` → the
  versioned `content/v1` / `identity/v1` / `permissions/v1` payloads.
- [ ] Message-to-text and channel/member-to-identity rendering; a slim ID+ACL
  pull for cheap permission-only re-crawls.
- [ ] Token provider resolved per connection and never logged. Open: the official
  Slack SDK or a plain HTTP client, and an environment-resolved token or an
  encrypted store. Decide with the credential-handling rules in view and record
  only the managed secret location.
- [ ] Cursor pagination, `Retry-After` handling with bounded backoff, and
  per-item failure isolation with a threshold abort.
- [ ] Adapter tests against recorded Slack API responses; no live network in CI.

Gate: `.\gradlew.bat test`.

## Phase C — Real workspace and consolidation

- [ ] Documented run against a free Slack workspace with login through the local
  Keycloak: a real channel becomes retrievable only by mapped members, and
  removing a member closes access on the next crawl. Confirm at this point
  whether the scopes need anything a free workspace cannot grant.
- [ ] Regression across core, api, worker, and the OpenFGA model.
- [ ] Update the knowledge-ingestion spec, `ARCHITECTURE.md`, and docs/tests with
  the live connector and the identity-trust rule; move this increment to
  `completed`.

## Credentials the operator supplies

A Slack app with `channels:read`, `channels:history`, `groups:read`,
`groups:history`, `users:read`, `users:read.email`; the bot token in `.env` as
`ORGMEMORY_SLACK_BOT_TOKEN` and never committed; the bot invited to the crawled
channel; member emails matching `app_users.email`.
