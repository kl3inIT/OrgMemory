# Google Drive Connector Plan

Four phases. Each is a commit that leaves the build green.

## Phase 1 — The credential probe becomes a port

- [x] `ConnectorCredentialProbe` in `core` with a `ConnectorCredentialProbeResult`
  whose vocabulary is not Slack's: connection key, account name, identity name,
  whether content can be read, error code.
- [x] `ConnectorCredentialProbeRegistry`, built from contributed beans the way
  `ConnectorSourceRegistry` is, refusing a source that contributed none.
- [x] `SlackCredentialProbe` implements the port. `AdminConnectorController`
  stops importing anything from `com.orgmemory.connectors.slack`.

Gate: `.\gradlew.bat :core:test :integrations:connectors:test :apps:api:test`,
contract and client regenerated. No reference to a named source in `apps/api`.

## Phase 2 — The adapter

- [x] `GoogleServiceAccountKey` parses the JSON key; `GoogleAccessTokenSource`
  signs an RS256 JWT and exchanges it, caching the token until it expires.
- [x] `GoogleDriveApiClient` over `RestClient`: paging, `files.list` with inline
  permissions, `files.export`, `files.get?alt=media`, and Google's error shape.
- [x] `GoogleDriveConnectorBatchSource` produces the same crawl contract, with the
  same content/permission split on the same interval logic.
- [x] `GoogleDriveSourceProfile`, `GoogleDriveCrawlSettings`,
  `GoogleDriveCredentialProbe`, contributed from an auto-configuration.

Gate: `.\gradlew.bat :integrations:connectors:test` with recorded responses only;
nothing touches the network.

## Phase 3 — The browser stops knowing which source it is looking at

- [x] Drive's mark in `SourceIcon`; a catalogue entry; a field descriptor.
- [x] The credential step becomes declarative — label, placeholder, whether it is
  multi-line, what has to be granted — so the wizard serves any source.
- [x] `/admin/connectors/$sourceSystem` replaces `/admin/connectors/slack`.

Gate: `pnpm lint`, `typecheck`, `build`.

## Phase 4 — Prove it and record it

- [x] Adapter tests against recorded Drive responses, mirroring the Slack suite:
  paging, the permission mapping including what it deliberately does not grant,
  export versus download, completeness withdrawal, and a refused credential.
- [x] An API test proving two sources are reported and each probes with its own
  adapter.
- [x] Consolidate into the specs, `ARCHITECTURE.md`, docs/tests; move to
  `completed`, recording what the second source cost that the first did not
  predict.

Gate: `.\gradlew.bat clean test`, then `pnpm lint`, `typecheck`, `build`.

## The measurement

It held. No migration was written, no endpoint was added, and nothing in `core`
learned a source name. Two greps are the evidence and both are empty:
`core/src/main` matching a source name outside a comment, and `apps/api/src/main`
importing `com.orgmemory.connectors`.

Two things had to move, and both were named in the design before starting rather
than discovered during it:

- The credential probe became a port. That was deferred deliberately at one
  implementation, and building it from two real cases produced a different result
  than building it from one would have: the neutral vocabulary
  (`connectionKey`/`accountName`/`identityName`/`canReadContent`) came out of
  Drive's shape refusing to fit Slack's field names, not out of foresight.
- The wizard's credential step became declarative, which is the same answer its
  configuration step already had. What was not obvious in advance is that the
  credential descriptor needed `multiline` — a service account key is a JSON
  document and a bot token is one line, and no amount of thinking about Slack
  would have produced that field.

One thing was expected to be hard and was not. Adding a second source to every
screen cost nothing, because the list page had already been rewritten to read
`GET /sources` rather than to name Slack. That rewrite happened one increment
earlier for an unrelated reason — the page was showing a "Slack" heading over an
empty table — which is the ordinary way these things pay off.

Still unproved: neither adapter has run against a real workspace or a real Drive.
