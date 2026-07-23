# Slack Connector Live Plan

Three phases. Phase A is core-side and needs no Slack credential, so it ships
first and the adapter lands against a stable core. Each item is a commit that
leaves the build green.

## Phase A — Core (no credentials)

- [x] Consume `identity_trust`: resolve the batch's connection decision once per
  crawl and pass it into automatic matching, so `SSO_EMAIL_JOIN` fires when the
  source vouches for the principal *or* an administrator attested the connection.
- [x] Content-edit re-materialization: a changed content revision appends a new
  source revision and re-runs normalize/publish instead of reporting
  `ROTATED_CONTENT_DEFERRED`.
- [x] Persisted per-connection crawl checkpoint replacing the in-process cursor
  set in `ConnectorCrawlRunner`; bounded per-batch retry.
- [x] Deletion detection: diff the indexed set against the crawled set and emit
  tombstones, gated on the batch declaring a complete crawl.

Gate: `.\gradlew.bat :core:test` and the worker connector slice, both proved
through the existing `FileConnectorBatchSource`.

## Phase B — Slack adapter

- [x] New `integrations:connectors` module; `SlackConnectorBatchSource` in
  `com.orgmemory.connectors.slack` implementing `ConnectorBatchSource` over
  `conversations.list`/`history`/`replies`/`members` and `users.list` → the
  versioned `content/v1` / `identity/v1` / `permissions/v1` payloads.
- [x] Message-to-text and channel/member-to-identity rendering, threaded on
  `channelId__threadTs`, with the completeness claim withdrawn whenever the crawl
  did not in fact see the whole connection.
- [x] Token provider resolved per connection and never logged. Decided: Spring
  `RestClient` over the official SDK, and an environment-resolved token
  (`ORGMEMORY_CONNECTOR_SLACK_BOT_TOKEN`) over an encrypted store. The store is
  the right answer for many connections and replaces the provider without
  anything else moving; recording that here rather than pretending it is built.
  **Superseded** by `2026-07-23-slack-connection-admin`: connections and their
  encrypted tokens now live in the ledger, and the environment-resolved provider
  is gone. It did replace without anything else moving.
- [x] Cursor pagination and `Retry-After` handling with bounded backoff, applied
  before a request rather than only after a refusal.
- [x] Adapter tests against recorded Slack API responses; no live network in CI.
- [x] Slim ACL pull between content crawls: channels and their members from
  Slack, applied to the objects the ledger already holds through
  `ConnectorObjectDirectory`. A call per channel rather than a call per thread,
  and never a completeness claim, because its object list is our own record.
- [x] Threshold abort, plus an `auth.test` preflight so a dead credential says
  so instead of surfacing as every channel failing at once.
- [x] Slack markup resolved out of the indexed body, threads deduped across a
  broadcast reply, and `app_id` posts filtered — read across from Onyx.

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
`groups:history`, `users:read`, `users:read.email`; the bot invited to the
crawled channel; member emails matching `app_users.email`.

The token and the connection's OrgMemory half — which workspace, which Knowledge
Space, which actor — are entered in the browser under `/admin/connectors` and
stored encrypted, not configured on the host. Nothing runs until an administrator
has enabled a connection there. The worker still needs
`ORGMEMORY_CONNECTOR_SCHEDULING_ENABLED=true` to poll at all, which is the one
remaining deployment-level decision: whether this process crawls.
