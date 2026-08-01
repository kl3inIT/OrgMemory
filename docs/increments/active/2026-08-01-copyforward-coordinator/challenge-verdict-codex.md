# Copy-Forward Coordinator Architecture Challenge Verdict

**Reviewed OrgMemory commit:** 0cb8a1873689f0af0327c3e903d09567375516f2 on increment/copyforward-coordinator
**Comparable checkout:** Onyx 618b5031bf21463f44e3bed9eb9d5073b806fec0 (local main, 224 commits behind its remote)
**Review mode:** adversarial, read-only; no files, code, tests, or runtime state were changed.

## Executive Verdict

| Decision | Verdict | Reason |
|---|---|---|
| 1. Extract one OpenSearchCopyForwardCoordinator and standardize one protocol | **APPROVE-WITH-MUST-FIX** | Consolidation is justified, but “choose one of the three” is the wrong framing: only the staged helper has the locally safe retired-lock handoff, while **none** of the three has a complete durable COPYING/FAILED/READY protocol for cross-process ownership and crash recovery. |
| 2. Use server-side _reindex if conditions hold; otherwise stream page→bulk | **REJECT _reindex; APPROVE the streaming fallback** | The Java client supports _reindex, but current OrgMemory copy-forward must rewrite batch_id, generation, and sometimes _id; vector/content also copy within the same physical index, while OpenSearch requires different source and destination indexes. No-script _reindex therefore cannot implement the current contract. |

## 1. The Three Current Protocols

### Common durable marker shape and states

All three use a static, process-local ConcurrentHashMap<String, ReentrantLock> and a durable control-index marker. The durable marker has copy_status=COPYING while copying and copy_status=READY after completion; there is no FAILED, ABANDONED, lease-expiry, or heartbeat state.

- Staged helper local map: integrations/graph-rag-opensearch/src/main/java/com/orgmemory/graphrag/opensearch/OpenSearchStagedIndex.java:25.
- Lexical local map: integrations/graph-rag-opensearch/src/main/java/com/orgmemory/graphrag/opensearch/OpenSearchLexicalIndex.java:41.
- Vector local map: integrations/graph-rag-opensearch/src/main/java/com/orgmemory/graphrag/opensearch/OpenSearchVectorIndex.java:29.
- Staged writes COPYING plus owner/start time: OpenSearchStagedIndex.java:196.
- Lexical writes the same fields: OpenSearchLexicalIndex.java:266.
- Vector writes the same fields: OpenSearchVectorIndex.java:230.
- Their only terminal check is literal READY: OpenSearchStagedIndex.java:385, OpenSearchLexicalIndex.java:338, OpenSearchVectorIndex.java:372.

### Precise differences

| Aspect | OpenSearchStagedIndex | OpenSearchLexicalIndex | OpenSearchVectorIndex |
|---|---|---|---|
| Marker / lock key | copy:{batchId}:{projectionKind}:{unsigned targetIndex.hashCode base36} (OpenSearchStagedIndex.java:376). | copy:{batchId}:LEXICAL (OpenSearchLexicalIndex.java:334). | copy:{batchId}:VECTOR (OpenSearchVectorIndex.java:368). |
| Why key differs | One helper instance backs content plus both graph partitions. Graph entity and relation targets share ProjectionKind.GRAPH, so target identity must distinguish them (OpenSearchGraphStore.java:47, OpenSearchGraphStore.java:54). | One lexical physical target exists per batch (OpenSearchIndexNames.java:16). | One logical vector copy spans every profile/dimension index matching the vector pattern (OpenSearchIndexNames.java:41, OpenSearchVectorIndex.java:289). |
| Same-JVM second caller mid-copy | Blocks on the same lock. After acquiring, it verifies that the lock is still the current map entry before touching the marker (OpenSearchStagedIndex.java:179). | Blocks on the same lock, then re-reads the marker (OpenSearchLexicalIndex.java:258). | Blocks on the same lock, then re-reads the marker (OpenSearchVectorIndex.java:222). |
| Cross-JVM second caller mid-copy | Does not block: static locks are process-local. Any non-READY marker, including a live COPYING, is CAS-replaced by a new owner (OpenSearchStagedIndex.java:190, OpenSearchStagedIndex.java:215). | Same immediate takeover (OpenSearchLexicalIndex.java:262, OpenSearchLexicalIndex.java:279). | Same immediate takeover (OpenSearchVectorIndex.java:226, OpenSearchVectorIndex.java:242). |
| Success retirement | Removes map entry before unlocking; a waiter holding the retired lock detects identity mismatch and loops to the current entry (OpenSearchStagedIndex.java:184, OpenSearchStagedIndex.java:249, OpenSearchStagedIndex.java:251). | Unlocks, then removes in finally (OpenSearchLexicalIndex.java:299). | Unlocks, then removes in finally (OpenSearchVectorIndex.java:262). |
| Failed copy | Durable marker remains COPYING. The map entry remains, because removals exist only on successful/observed READY paths; finally only unlocks (OpenSearchStagedIndex.java:192, OpenSearchStagedIndex.java:224, OpenSearchStagedIndex.java:251). | Durable marker remains COPYING, but finally unlocks and removes the map entry (OpenSearchLexicalIndex.java:284, OpenSearchLexicalIndex.java:299). | Durable marker remains COPYING, but finally unlocks and removes the map entry (OpenSearchVectorIndex.java:247, OpenSearchVectorIndex.java:262). |
| Next same-batch call after failure | Reuses the retained lock and CAS-replaces the stale marker, then recopies (OpenSearchStagedIndex.java:180, OpenSearchStagedIndex.java:215). | Creates/reuses a new map entry and CAS-replaces the stale marker, then recopies (OpenSearchLexicalIndex.java:258, OpenSearchLexicalIndex.java:279). | Creates/reuses a new map entry and CAS-replaces the stale marker, then recopies (OpenSearchVectorIndex.java:222, OpenSearchVectorIndex.java:242). |

The phase-2 V5 statement is therefore accurate: the staged map entry, not a locked lock, survives (docs/increments/completed/2026-08-01-authz-consolidation/references/phase2-codex-report.md:96).

### Which current behavior should be standardized?

**Use the staged helper’s local retirement algorithm as the local-lock baseline, not its entire failure policy.** Its remove-before-unlock plus map-identity retry prevents an old waiter from operating under a retired lock while a new caller operates under a replacement lock (OpenSearchStagedIndex.java:184). Lexical/vector’s unlock-then-remove ordering permits an old waiter to acquire the retired lock just before removal while another caller creates a replacement lock; durable CAS usually converts this into a spurious loser, but it is unnecessary split-brain at the JVM-lock layer (OpenSearchLexicalIndex.java:299, OpenSearchVectorIndex.java:262).

However, **none is correct to standardize unchanged**:

1. A durable COPYING marker can be stolen immediately by another JVM because every non-READY marker is treated as retryable (OpenSearchStagedIndex.java:215, OpenSearchLexicalIndex.java:279, OpenSearchVectorIndex.java:242).
2. There is no durable failed state, lease deadline, heartbeat, or explicit retirement record.
3. OpenSearchStagedIndex uses 32-bit Java String.hashCode() as target identity (OpenSearchStagedIndex.java:382); graph genuinely needs a target discriminator, but a collision-prone hash is not a sound durable identity.
4. A projection that throws during prepare is not added to prepared; lifecycle abort/discard only covers entries added after a successful prepare (components/graph-rag-core/src/main/java/com/orgmemory/graphrag/storage/ProjectionBatchLifecycle.java:42, ProjectionBatchLifecycle.java:63). Partial writes and the current projection’s marker can therefore survive failure even though earlier projections are discarded.

### Required standardized protocol

The coordinator must define, test, and document this protocol rather than merely selecting one existing copy:

- Key by exact batchId + logical copy unit + canonical target identity; retain a target discriminator for graph entity/relation partitions. Do not use one global lock and do not use String.hashCode() as durable identity.
- Durable states must be explicit: at minimum COPYING(owner, attempt/fence, started, lease-or-job identity), READY, and FAILED/ABANDONED.
- A second process observing a live COPYING must wait/fail retryably; it must not CAS-steal immediately.
- On an owned failure, transition to FAILED before releasing local ownership. A retry may claim only FAILED, never a live COPYING.
- For a hard crash, prefer poisoning/aborting that batch and retrying under a new batch ID, then clean unreachable partial data. A timestamp-only lease takeover is insufficient because a paused old owner can resume and write after its lease expires unless writes are fenced.
- Keep the staged helper’s remove-before-unlock and current-map-entry check for local lock retirement.
- Ensure cleanup includes the currently failing projection, not only previously prepared projections.
- Add adversarial tests for same-JVM waiters, two independent coordinator instances (simulated cross-JVM), failure after a partial page/bulk, crash-stale COPYING, graph’s two targets, and retry cleanup.

No existing retirement difference encodes a valid per-index requirement. The key/copy-plan shape does: graph has two targets under one projection kind, vector spans multiple shared physical indexes, lexical has one per-batch index, and content uses one shared index.

## 2. Cross-Index Interleaving and Publication Failure Story

One worker publication includes CONTENT, LEXICAL, VECTOR, and GRAPH in one ProjectionBatch (apps/worker/src/main/java/com/orgmemory/worker/graph/GraphPublicationCommitter.java:46, GraphPublicationCommitter.java:111). ProjectionBatchLifecycle sorts by enum ordinal and prepares sequentially (ProjectionBatchLifecycle.java:38); the enum order is CONTENT → LEXICAL → VECTOR → GRAPH (components/graph-rag-core/src/main/java/com/orgmemory/graphrag/storage/ProjectionKind.java:3). Graph then touches entity before relation through its staged helpers (OpenSearchGraphStore.java:74, OpenSearchGraphStore.java:97).

Therefore:

- Within one publication call there is no lexical/vector/graph copy overlap; current ordering is sequential.
- Concurrent publisher calls use different random batch IDs (GraphPublicationCommitter.java:111). Their marker keys therefore differ. They may interleave across kinds, but their staged data is isolated: lexical and graph use batch-specific physical indexes (OpenSearchIndexNames.java:16, OpenSearchIndexNames.java:23); content and vector include batch ID in physical document IDs (OpenSearchStagedIndex.java:372, OpenSearchVectorIndex.java:295).
- The namespace-head CAS, not copy-forward locks, chooses the generation winner (OpenSearchProjectionPublicationStore.java:151, OpenSearchProjectionPublicationStore.java:172). A losing publication is restored/fails rather than becoming readable (OpenSearchProjectionPublicationStore.java:182).
- A coordinator keyed per copy unit does not change ordering. A single global coordinator mutex or one marker per batch would change it and would wrongly collapse graph’s two sub-index copies.
- The existing failure gap remains: if the current adapter fails during preparation, lifecycle aborts but does not discard that current adapter because it was never appended to prepared (ProjectionBatchLifecycle.java:42, ProjectionBatchLifecycle.java:48). Consolidation must close this; otherwise the coordinator only centralizes the leak.

## 3. _reindex Decision

### Client support and permissions

The repository pins org.opensearch.client:opensearch-java 3.9.0 (gradle/libs.versions.toml:11, gradle/libs.versions.toml:28). The locally resolved 3.9.0 JAR contains ReindexRequest, ReindexResponse, and rethrottle classes, and the official Java guide exposes client.reindex(...): https://github.com/opensearch-project/opensearch-java/blob/main/guides/document_lifecycle.md

Client support does not establish deployment authorization:

- Current production explicitly excludes production OpenSearch (docs/increments/active/2026-07-25-production-cicd-zm/design.md:271).
- The shared ZM runtime has no OpenSearch container (docs/increments/active/2026-07-31-shared-zm-team-development/design.md:24).
- The integration test disables the Security plugin (integrations/graph-rag-opensearch/src/test/java/com/orgmemory/graphrag/opensearch/OpenSearchProjectionPublicationIntegrationTests.java:68).
- Runtime properties accept endpoint/basic credentials but define no role or permission contract (OpenSearchGraphRagProperties.java:10, OpenSearchGraphRagAutoConfiguration.java:60).

OpenSearch documents indices:data/write/reindex as required: https://docs.opensearch.org/latest/api-reference/document-apis/reindex/ . No current deployment file grants or verifies it. The permission condition is therefore **unproven and currently inapplicable**, not satisfied.

### Byte/field/ID semantics: no-script _reindex is inadmissible

OpenSearch _reindex extracts _source and indexes it into a different destination; it does not copy index settings/mappings, and source and destination must differ: https://docs.opensearch.org/latest/api-reference/document-apis/reindex/ . This is re-indexing, not a Lucene-byte clone. With no script/pipeline it preserves the old _source field values and _id; destination mapping is applied again.

OrgMemory copy-forward deliberately changes generation coordinates:

- Staged content/graph copies replace batch_id and generation, and generate a new batch-prefixed _id (OpenSearchStagedIndex.java:273, OpenSearchStagedIndex.java:287).
- Lexical copies replace batch_id and generation (OpenSearchLexicalIndex.java:320).
- Vector copies replace batch_id and generation, and generate a new batch-prefixed _id (OpenSearchVectorIndex.java:290).

Those fields are required by snapshot reads (OpenSearchStagedIndex.java:327, OpenSearchLexicalIndex.java:148, OpenSearchVectorIndex.java:321). Leaving old values makes copied documents invisible to the new snapshot and breaks later delete/upsert identity.

Physical layout is a second hard blocker:

- Content source and destination are the same shared content index (OpenSearchIndexNames.java:12).
- Vectors are copied within the same profile/dimension indexes (OpenSearchIndexNames.java:41, OpenSearchVectorIndex.java:296).
- OpenSearch forbids reindexing an index into itself.

A script could change _source and ctx._id, but that violates the stated no-scripting condition and expands the security/verification surface. Therefore server-side _reindex is not admissible under the current index and snapshot contract.

### Failure mapping

Synchronous _reindex can return HTTP success with timed_out=true and/or a non-empty failures array after partially writing the destination. Existing bulk copy also leaves successful sub-operations in place but converts any item failure into a whole-operation exception (OpenSearchOperations.java:202). A coordinator could map _reindex to the same caller-visible whole-operation failure only by checking timed_out, every failure, and completion counts, refusing READY, and cleaning/retrying partial output. That mapping is possible in principle, but it is not automatic and does not solve the semantic blockers above.

Official response/partial-failure fields: https://docs.opensearch.org/latest/api-reference/document-apis/reindex/

### Very large generations

- Synchronous reindex would be exposed to the adapter’s 30-second default socket timeout (OpenSearchGraphRagProperties.java:18, OpenSearchGraphRagAutoConfiguration.java:51).
- wait_for_completion=false returns a task ID and continues in the cluster; correctness would require persisting the task ID, polling Tasks API to terminal completion, canceling on batch abort, and preventing a late task from writing after ownership retirement.
- Default requests_per_second=-1 is unthrottled; large copies need explicit throttling and possibly slicing, with resource monitoring.
- A request timeout can leave a partially completed destination; it is not rollback.

The current design contains none of that task lifecycle. Adding it would be a separate durable-job protocol, not a transport optimization.

### Approved fallback: genuinely stream page→bulk

The fallback should be implemented, but current OpenSearchScanner.scan is not streaming to its caller: it accumulates every PIT page into one ArrayList and returns a full immutable list (OpenSearchScanner.java:44, OpenSearchScanner.java:75). Current copy implementations then build a second full list/map of bulk operations (OpenSearchStagedIndex.java:269, OpenSearchLexicalIndex.java:320, OpenSearchVectorIndex.java:289).

Must implement a page consumer/iterator that:

1. holds the PIT;
2. reads one bounded page;
3. transforms only generation coordinates/ID;
4. bulk-writes that page;
5. verifies the page response;
6. releases page memory before advancing;
7. closes the PIT in finally.

The bound should cover payload bytes as well as operation count because vector pages can dominate heap even at 500 records; current bulk batching is count-only (OpenSearchOperations.java:92).

## 4. Fingerprint / Manifest Path

Copy-forward feeds **no** manifest/fingerprint/digest computation, directly or by read-back.

Exact path:

1. GraphPublicationCommitter computes the manifest from the claimed chunks, graph projection manifest, embedding profile, and graph-processing profile before creating the batch (GraphPublicationCommitter.java:320).
2. The computed string is placed in ProjectionBatch before staging begins (GraphPublicationCommitter.java:111).
3. The publication store treats it as opaque control-plane data in the batch/receipt documents (OpenSearchProjectionCodec.java:229).
4. markPrepared compares the supplied fingerprint against the receipt; it does not scan staged indexes (OpenSearchProjectionPublicationStore.java:113).
5. requirePrepared reads only control-index receipts and compares the opaque value (OpenSearchProjectionPublicationStore.java:382).

Copy-forward reads prior _source documents and changes only the generation coordinates in the current implementation (OpenSearchStagedIndex.java:269, OpenSearchLexicalIndex.java:320, OpenSearchVectorIndex.java:290). It does not recalculate content_sha256, projection manifests, or the publication fingerprint.

This means fingerprint coupling does not independently forbid server-side copy. It also means “byte identity” must be stated precisely: exact document bytes cannot remain identical because batch_id/generation and sometimes _id must change; the invariant is preservation of every non-coordinate payload field, not equality of the whole stored document.

## 5. Comparable System: Onyx

Onyx does **client-side streaming/transform/write**, not server-side _reindex, for PRESENT→FUTURE index movement:

- Its port copier explicitly PIT-scans PRESENT, re-embeds for FUTURE, and writes FUTURE in bounded pages (D:/OrgMemory/tmp/onyx/backend/onyx/document_index/opensearch/port_copy.py:1, port_copy.py:81).
- It brackets expensive work with cancellation/heartbeat checks and rechecks surviving documents immediately before every sub-page write (port_copy.py:117, port_copy.py:139).
- It uses create-only writes so a stale backfill cannot overwrite a live/forward writer; 409 conflicts are benign, every other bulk error fails (D:/OrgMemory/tmp/onyx/backend/onyx/document_index/opensearch/opensearch_document_index.py:945, D:/OrgMemory/tmp/onyx/backend/onyx/document_index/opensearch/client.py:1026, client.py:1063).
- Durable coordination is a PostgreSQL PortAttempt, with partial unique indexes allowing at most one active attempt per scope/FUTURE settings (D:/OrgMemory/tmp/onyx/backend/onyx/db/models.py:2557, models.py:2639).
- Status transitions are serialized with SELECT ... FOR UPDATE; duplicate task dispatch cannot create a second writer (D:/OrgMemory/tmp/onyx/backend/onyx/db/port_attempt.py:53, port_attempt.py:557).
- Progress cursor and heartbeat are durable, failures resume from the last committed cursor, and a watchdog fails stalled attempts (D:/OrgMemory/tmp/onyx/backend/onyx/background/celery/tasks/port/tasks.py:309, tasks.py:419, tasks.py:490).
- Redis locking only serializes the scheduler beat; it is not the ownership authority for each copy (tasks.py:722).
- Promotion is a database status swap gated on all required port attempts and reconciliation work (D:/OrgMemory/tmp/onyx/backend/onyx/db/swap_index.py:94, swap_index.py:216).

Onyx is not proof that OrgMemory should copy its design: Onyx re-embeds and tolerates forward writes during migration, while OrgMemory builds an immutable unpublished generation. It is strong counterevidence to an unqualified _reindex optimization and strong evidence for bounded page streaming, durable attempts, explicit progress, and stale-writer protection.

## Must-Fix List

1. Define a durable marker state machine; never steal a live COPYING marker merely because it is non-READY.
2. Use staged-style remove-before-unlock plus current-map-entry validation for local retirement.
3. Replace targetIndex.hashCode() with collision-resistant/exact canonical target identity.
4. Preserve per-copy-unit keys; no global coordinator lock and no single marker that collapses graph entity/relation.
5. Ensure the currently failing projection is cleaned, not only projections that already returned successfully.
6. Add crash/partial-page/split-process/retired-lock tests before calling the protocol standardized.
7. Reject no-script _reindex in the implementation plan for the current layout; do not leave it as an open optimization.
8. Implement actual page→bulk streaming with count and byte bounds.
9. Preserve and assert every non-coordinate _source field; explicitly test vectors, nested maps/lists, numeric values, and IDs.
10. If _reindex is reconsidered after an index-layout redesign, first add secured-cluster permission tests, task persistence/poll/cancel semantics, throttling, timeout handling, and partial-output cleanup.

## Strongest Counterargument

The strongest case for the proposal is that _reindex is a first-class operation in the pinned client, moves heap/network work to the cluster, returns explicit failures, supports async tasks/throttling/slicing, and copy-forward does not participate in the producer manifest. For lexical and graph, source and target are already different per-batch indexes, so server-side copying appears especially attractive.

That argument does not survive current repository semantics. Even lexical/graph must rewrite batch_id/generation, staged graph uses new batch-prefixed physical IDs, content/vector require same-index generation duplication, and deployment permissions are absent/unverified. Allowing scripts would defeat the stated condition and add a new security surface. The optimization becomes viable only after a materially different physical-generation layout or an explicitly approved scripted/pipeline transformation protocol.

## Scope Limits

- This verdict covers the copy-forward coordinator and _reindex choice only. It does not review B20 refresh-policy changes.
- No secured OpenSearch deployment exists in the reviewed production/ZM configuration, so real role behavior could not be runtime-tested.
- No tests were run because the assignment was strictly read-only; conclusions come from source, the resolved 3.9.0 client artifact, and current official OpenSearch documentation.
- The OrgMemory branch is one commit behind origin/main; the active increment directory is untracked. The verdict is pinned to the SHA above.
- The Onyx comparison is pinned to its local SHA above and may lag upstream; it is comparable evidence, not an authority over OrgMemory’s immutable-generation contract.