# Architecture Challenge: Bounded Google Drive Ingestion

## Reviewer mandate

Act as an adversarial, read-only architecture reviewer. Attack the proposal; do
not validate it. Verify every material claim in repository code. Do not edit
files, change configuration, run migrations, or implement a solution.

Read `AGENTS.md`, `docs/conventions.md`,
`docs/guidelines/agent-safety.md`,
`docs/specs/domains/knowledge-ingestion.md`,
`docs/tests/domains/knowledge-ingestion.md`, decision filenames under
`docs/decisions/`, and this increment's `design.md` and `plan.md`. Inspect every
source path cited below and the pinned Onyx checkout at `D:/OrgMemory/tmp/onyx`.

Return a structured Markdown verdict with:

1. `ACCEPT`, `ACCEPT WITH BINDING CORRECTIONS`, or `REJECT`;
2. one committed architecture and exact ownership boundaries;
3. the strongest counterargument and whether it defeats the choice;
4. exact aggregate-budget, partial-progress, retry, checkpoint, and retirement
   semantics;
5. must-fix implementation and test gates;
6. repository evidence for every claim;
7. rejected alternatives and concrete conditions that reopen them.

After the first verdict, challenge it with at least three contradictions: a
native Google document without size metadata, starvation of files after the
budget in stable listing order, and a worker crash after provider reads but
before reconciliation.

## Product promise at stake

OrgMemory is a governed organizational memory layer. Google Drive evidence must
enter the same immutable revision, sealed source-ACL, and permission-aware
retrieval path as every other source. A Drive crawl must not widen access, retire
unseen evidence, or exhaust the worker before reaching a durable boundary.

## Exact proposal under review

> Keep Google APIs and pre-ledger batching in `integrations:connectors`, polling
> and bounded retry in `apps:worker`, and durable source revisions, ACL
> generations, lifecycle, and retrieval enforcement in `core`. Do not add a
> Google-specific staging schema. Add an adapter-owned aggregate extracted-body
> budget, `maxBatchBytes`. Count each retained body's UTF-8 bytes. If the next
> body would exceed the budget, stop reading further bodies, mark CONTENT and
> whole-crawl completeness false, preserve already established permission
> evidence, and replay from Drive on a later poll. This is a bounded pilot
> boundary, not a claim of scalable change-token ingestion.

The reviewer must decide whether that rule is internally correct and sufficient
for the enterprise demo, or whether durable per-object staging is mandatory now.
If accepting corrections, define them precisely enough to implement without a
second design decision.

## OrgMemory repository evidence

| Fact | Evidence |
| --- | --- |
| The Drive adapter lists every in-scope file before observing bodies, then retains each extracted body in `crawl.contents`; the final `ConnectorCrawlBatch` carries all content strings. | `integrations/connectors/src/main/java/com/orgmemory/connectors/googledrive/GoogleDriveConnectorBatchSource.java:154-232,344-387`; `core/src/main/java/com/orgmemory/core/knowledge/connector/ConnectorCrawlBatch.java:10-49` |
| Current defaults cap one crawl at 500 files and one metadata-sized file at 10 MiB; Google-native exports have no size metadata and rely on the API client's 25 MiB response cap. No aggregate bound exists. | `integrations/connectors/src/main/java/com/orgmemory/connectors/googledrive/GoogleDriveCrawlSettings.java:28-44,70-87`; `GoogleDriveConnectorBatchSource.java:417-432`; `GoogleDriveApiClient.java:46-50` |
| Complete-crawl is retirement authority. A filter, provider gap, file bound, unreadable sharing, unreadable file, or permissions-only pass must not retire unseen objects. | `GoogleDriveConnectorBatchSource.java:44-47,158-171,183-231,353-377`; `ConnectorCrawlBatch.java:28-33` |
| The worker polls batches, computes independently pending components, retries three times, advances only completed component checkpoints, and records rejected/partial/failed attempts. | `apps/worker/src/main/java/com/orgmemory/worker/connector/ConnectorCrawlRunner.java:20-35,59-137` |
| Core resolves identities once and reconciles membership, content, permission-only updates, tombstones, and pruning under independent component completion. Content materialization intentionally crosses independently committed durable steps rather than one outer transaction. | `core/src/main/java/com/orgmemory/core/knowledge/connector/ConnectorIngestionService.java:24-31,58-173` |
| Current vertical retrieval proof covers Slack and GitHub fixture phases, not a real Google adapter-to-ledger path. | `apps/worker/src/test/java/com/orgmemory/worker/connector/ConnectorStagingIngestionIntegrationTests.java`; `docs/tests/domains/knowledge-ingestion.md:183-218` |
| The Drive adapter deliberately leaves Google Group/domain membership incomplete; incomplete membership grants nothing downstream. | `GoogleDriveConnectorBatchSource.java:205-215`; `docs/specs/domains/knowledge-ingestion.md:185-207` |
| Existing accepted boundaries put retryable ingestion in the worker, require idempotency/checkpoint/tombstone semantics, and keep provider adapters behind versioned staging contracts. | `docs/decisions/0008-worker-owns-ingestion-and-derived-indexes.md`; `docs/decisions/0026-connector-polling-lifecycle.md` |

## Comparable-system evidence

Pinned Onyx commit: `618b5031bf21463f44e3bed9eb9d5073b806fec0`.

| System | Behavior | Source |
| --- | --- | --- |
| Onyx | A database `IndexAttempt` fences and records one connector indexing attempt before fetching begins; failed attempts are marked rather than represented only by process memory. | `tmp/onyx/backend/onyx/background/celery/tasks/docfetching/task_creation_utils.py:21-124`; `tmp/onyx/backend/onyx/indexing/index_attempt.py` |
| Onyx | `ConnectorRunner` streams provider documents through a generator and emits bounded batches/checkpoints instead of constructing one all-content object for the entire connection. | `tmp/onyx/backend/onyx/connectors/connector_runner.py:28-290`; `tmp/onyx/backend/onyx/connectors/interfaces.py:294-322` |
| Onyx | The indexing worker advances checkpoint state throughout docfetching and separates provider fetch from later chunk/index batches. | `tmp/onyx/backend/onyx/background/indexing/run_docfetching.py:324-910`; `tmp/onyx/backend/onyx/background/indexing/run_indexing.py` |
| Onyx | Google Drive content sync and group/permission sync are separate jobs; Directory/Admin API access is not implied by Drive read access. | `tmp/onyx/backend/ee/onyx/external_permissions/google_drive/doc_sync.py`; `group_sync.py`; `folder_retrieval.py`; `permission_retrieval.py` |
| Onyx docs | Service account with domain-wide delegation is the recommended Workspace setup; individual-account OAuth is a separate mode. | `https://docs.onyx.app/admins/connectors/official/google_drive/service_account`; `.../oauth` |

Comparable behavior is evidence, not a parity requirement. OrgMemory has a
smaller pilot envelope and stricter immutable-ledger/source-ACL contracts.

## Observed operational cost

The default configuration permits a nominal retained payload of 500 * 10 MiB =
5,000 MiB before reconciliation, while Google-native documents can each consume
up to the separate 25 MiB response hard cap because their listing metadata omits
size. Java `String` and JSON/tree overhead make the real heap cost higher. A
single connector can therefore destabilize the worker before its first object is
written to the durable ledger. No production incident has occurred; this is a
code-derived pre-demo risk.

## Scope limits

The proposed increment does not claim Google Directory membership, OAuth,
multi-user domain enumeration, Change API/webhook incrementality, durable
pre-ledger staging, or large-tenant throughput. The reviewer must reject any
language that implies those capabilities.
