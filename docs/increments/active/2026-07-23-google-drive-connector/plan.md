# Google Drive Connector Plan

Four phases. Each is a commit that leaves the build green.

## Phase 1 — The credential probe becomes a port

- [ ] `ConnectorCredentialProbe` in `core` with a `ConnectorCredentialProbeResult`
  whose vocabulary is not Slack's: connection key, account name, identity name,
  whether content can be read, error code.
- [ ] `ConnectorCredentialProbeRegistry`, built from contributed beans the way
  `ConnectorSourceRegistry` is, refusing a source that contributed none.
- [ ] `SlackCredentialProbe` implements the port. `AdminConnectorController`
  stops importing anything from `com.orgmemory.connectors.slack`.

Gate: `.\gradlew.bat :core:test :integrations:connectors:test :apps:api:test`,
contract and client regenerated. No reference to a named source in `apps/api`.

## Phase 2 — The adapter

- [ ] `GoogleServiceAccountKey` parses the JSON key; `GoogleAccessTokenSource`
  signs an RS256 JWT and exchanges it, caching the token until it expires.
- [ ] `GoogleDriveApiClient` over `RestClient`: paging, `files.list` with inline
  permissions, `files.export`, `files.get?alt=media`, and Google's error shape.
- [ ] `GoogleDriveConnectorBatchSource` produces the same crawl contract, with the
  same content/permission split on the same interval logic.
- [ ] `GoogleDriveSourceProfile`, `GoogleDriveCrawlSettings`,
  `GoogleDriveCredentialProbe`, contributed from an auto-configuration.

Gate: `.\gradlew.bat :integrations:connectors:test` with recorded responses only;
nothing touches the network.

## Phase 3 — The browser stops knowing which source it is looking at

- [ ] Drive's mark in `SourceIcon`; a catalogue entry; a field descriptor.
- [ ] The credential step becomes declarative — label, placeholder, whether it is
  multi-line, what has to be granted — so the wizard serves any source.
- [ ] `/admin/connectors/$sourceSystem` replaces `/admin/connectors/slack`.

Gate: `pnpm lint`, `typecheck`, `build`.

## Phase 4 — Prove it and record it

- [ ] Adapter tests against recorded Drive responses, mirroring the Slack suite:
  paging, the permission mapping including what it deliberately does not grant,
  export versus download, completeness withdrawal, and a refused credential.
- [ ] An API test proving two sources are reported and each probes with its own
  adapter.
- [ ] Consolidate into the specs, `ARCHITECTURE.md`, docs/tests; move to
  `completed`, recording what the second source cost that the first did not
  predict.

Gate: `.\gradlew.bat clean test`, then `pnpm lint`, `typecheck`, `build`.

## The measurement

No migration, no new endpoint, no change to a `core` rule. Anything else this
increment has to touch is the answer to whether the abstraction was real, and it
gets written down rather than quietly absorbed.
