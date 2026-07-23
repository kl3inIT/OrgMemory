# Generic Connector Sources Plan

Four phases. Each is a commit that leaves the build green.

## Phase 1 — Separate the axes in the ledger

- [x] `V23`: `source_objects` gains `source_system`; `source_type` becomes
  `acl_authority` with `ORGMEMORY`/`SOURCE`; the identity constraint keys on
  `source_system` instead of the old type.
- [x] `AclAuthority` replaces `SourceType`. `SecureKnowledgeRetrievalStore` reads it.
- [x] `ConnectorSourceProfile` plus a registry built from contributed beans.
  `SlackConnectorProfile` leaves `core` for the Slack adapter.
- [x] `ConnectorReconciler`, `ConnectorObjectDirectory`, and
  `ConnectorIngestionService` resolve a profile instead of naming Slack.

Gate: `.\gradlew.bat :core:test :integrations:connectors:test :apps:worker:test`, and no
code reference to a named source in `core/src/main` — javadoc may still say "Slack" as an
example, and the applied V15 migration keeps whatever it always said.

## Phase 2 — Generic connection configuration

- [x] `V24`: `source_config jsonb` on `source_connections`; `channel_filter` and
  `max_threads_per_channel` move into it. The shared columns and their check
  constraints stay.
- [x] `SourceConnectionAdminService` and `ConnectorCrawlConfiguration` carry an
  opaque per-source map; the Slack adapter reads its own keys out of it.
- [x] `/api/admin/connectors/{sourceSystem}` replaces the Slack-specific path, and
  `GET /api/admin/connectors/sources` reports the profiles this deployment has.

Gate: `.\gradlew.bat :core:test :apps:api:test`, contract and client regenerated.

## Phase 3 — The catalogue as Onyx builds it

- [x] `SourceIcon` over inline brand SVG; a real Slack mark rather than a generic
  glyph. Inline because a mark fetched at runtime is a request that can be blocked.
  The same mark heads the Slack list section and the wizard.
- [x] Category grouping over the browser catalogue — but by custody, not by Onyx's
  `SourceCategory`. Onyx's axis sorts fifty-five business tools into wiki, storage,
  ticketing; at three entries it would make one heading per tile and say nothing the
  logo did not. Custody — does the access rule come from the source or from
  OrgMemory — is the axis that carries a fact, and it is the axis the ledger already
  records in `source_objects.acl_authority`.
- [x] Tiles render the intersection of the browser catalogue and what the backend
  reports; a source the deployment lacks is visibly unavailable, and says so in
  different words from a source the product has not built.
- Not done: the search box. Its usefulness is entirely a function of catalogue size,
  and over three tiles it is worse than looking at three tiles. The page filters a
  list, so it is a one-line addition when the catalogue earns it.

Gate: `pnpm lint`, `typecheck`, `build` in `web/`.

## Phase 4 — Declarative connection form and connection detail

- [ ] A field descriptor in the shape Onyx's `connectorConfigs` uses — text, list,
  checkbox, number, select, split into ordinary and advanced — and a renderer. The
  Slack wizard becomes a descriptor rather than a component.
- [ ] A connection detail page: objects indexed, last crawl, last failure.
- [ ] Consolidate into the specs, `ARCHITECTURE.md`, and docs/tests; move the
  increment to `completed`.

Gate: `.\gradlew.bat clean test`, then `pnpm lint`, `typecheck`, `build`.

## Proof the separation worked

Adding a source must be one adapter package with a `ConnectorSourceProfile` bean and one
entry in the browser catalogue. `ConnectorSourceRegistryTests` proves the ledger governs
only what an adapter declared: an uncontributed source is refused, and two adapters cannot
both claim one name.
