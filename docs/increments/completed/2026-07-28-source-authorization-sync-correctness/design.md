# Source Authorization Sync Correctness

## Outcome

Connector progress is tracked independently for content, permissions, and
membership without creating three checkpoint subsystems. Source-declared
incompleteness is durable health evidence but never becomes successful
authorization evidence. Technical and per-item failures remain pending.

The latest complete sealed source ACL remains authoritative after its freshness
timestamp, consistently in canonical knowledge retrieval and PostgreSQL
GraphRAG, as required by ADR 0015.

## Repository Evidence

- `connector_crawl_checkpoints` has one cursor per connection, so a membership
  change also makes unchanged content and permissions look new.
- `ConnectorCrawlRunner` records `SUCCEEDED` and advances the checkpoint even
  when `ConnectorIngestionResult.failures()` is non-empty. Those failed objects
  are never offered again when the producer returns the same cursor.
- Membership already distinguishes `COMPLETE` from `INCOMPLETE`, while the
  batch has no generic component capture state.
- Canonical knowledge SQL uses the latest sealed complete ACL after expiry, but
  `PostgresAuthorizedGraphSql` still requires `valid_until > evaluatedAt`,
  contradicting ADR 0015 and causing search/graph visibility drift.

## Decision

### One generic component checkpoint

Each batch declares one state for every component it carries:

```text
ConnectorComponentState
- component: CONTENT | PERMISSION | MEMBERSHIP
- cursor
- captureStatus: COMPLETE | INCOMPLETE
- incompleteReason (required only for INCOMPLETE)
```

The single checkpoint table is keyed by organization, source system,
connection, and component. It stores:

- the last observed cursor/status/reason/time;
- the last successfully reconciled cursor/time.

Successful `COMPLETE` capture advances both observations and success.
Successfully handled `INCOMPLETE` capture advances only observation. Technical
failure advances neither.

Identity observation remains batch context rather than a fourth independently
authorized component. It is replay-safe and is required to resolve permission
and membership principals.

### Partial batch execution

Per-item failures name the component or components they blocked. A batch with
such failures is `PARTIAL`, not `SUCCEEDED`.

The runner retries only components whose cursor was not observed successfully.
It may advance a successful component while another remains pending. An
unsupported/invalid payload remains `REJECTED` and is observed past because the
same bytes cannot become valid through retry.

An incomplete permission component never rotates resource ACL heads. Content
that cannot be safely materialized without complete permission evidence remains
pending. Incomplete membership items may be stored diagnostically because their
service already prevents sealing or activation.

### Stale ACL policy

Freshness is operational evidence, not the default authorization gate. Both
knowledge and GraphRAG require the current head to reference a sealed
`COMPLETE` snapshot, but neither denies solely because `valid_until` passed.
The component checkpoint view exposes permission-sync health and last success.

## Rejected Alternatives

- Three checkpoint tables/services: duplicates identical concurrency and
  reporting mechanics.
- Checkpointing the whole batch after partial success: permanently loses failed
  object work.
- Retrying source-declared incomplete evidence as a technical error: confuses a
  truthful source limitation with an unavailable worker and can hot-loop an
  unchanged source response.
- Re-enabling a universal 24-hour denial: contradicts accepted ADR 0015 and
  turns connector outage into knowledge outage.
- Dual-write/legacy checkpoint compatibility: disposable POC progress may be
  recreated; the canonical target is implemented directly.

## Exit Evidence

- Content, permission, and membership cursors advance independently in one
  checkpoint table.
- Incomplete observation is visible but leaves last-successful state unchanged.
- A per-item failure produces `PARTIAL`, does not advance its component, and is
  retried on the next poll.
- An incomplete permission component rotates no ACL and cannot materialize new
  content under guessed access.
- Expired but latest sealed complete ACL evidence yields the same decision in
  knowledge and PostgreSQL GraphRAG.
- Admin activity exposes component status and last-success time.
