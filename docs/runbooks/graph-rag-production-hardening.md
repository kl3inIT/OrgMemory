# GraphRAG Production Hardening Runbook

## Scope

This runbook closes operational evidence; it does not turn an unexecuted
procedure into a passing result. Store raw outputs with the release evidence
and record the exact commit, container digests, corpus hash, embedding profile,
authorization model, hardware and timestamps.

## Payload-Free Tracing

`integrations/graph-rag-observability` converts completed GraphRAG stage events
to OpenTelemetry spans. API and worker use Spring Boot's OpenTelemetry starter.
Configure an OTLP-compatible collector, including Langfuse, with Spring Boot
4.1 properties under:

```text
management.opentelemetry.tracing.export.otlp.endpoint
management.opentelemetry.tracing.export.otlp.headers
management.opentelemetry.tracing.export.otlp.transport
```

The application allowlist is operation and organization UUIDs, stage, outcome,
cache status, a monotonic duration, non-negative input and output counts, an
optional lowercase SHA-256 model-route fingerprint, an optional lowercase
SHA-256 scope fingerprint, a bounded machine failure code, an event timestamp,
the context token breakdown with the budget it was fitted to and the number of
contributions the budget evicted, and provider input/output token totals.

Two corrections to an earlier version of this list, both of which described a
stronger guarantee than the code gives:

- The counts are checked for non-negativity, not for an upper bound. Nothing
  rejects an implausibly large one, so a count is trusted to be a count and not
  proven to be a small one.
- The list omitted the scope fingerprint, the cache status and the event
  timestamp, which have always been exported. An allowlist that does not name
  everything exported is not an allowlist.

Never add query, prompt, completion, evidence/chunk text, document title/URI,
embedding values, actor identity, ACL subjects or exception messages. Token
counts are permitted where the text they measure is not, because a total cannot
reconstruct what it totalled. Spring AI prompt and completion observation
logging is explicitly disabled in API and worker configuration, and the provider
client packages are pinned above WARN because their own logging concatenates
payload independently of Spring AI.

Before claiming Langfuse compatibility as verified:

1. send one successful retrieval, one failed retrieval and one indexing job to
   the configured collector;
2. confirm the stage names and original durations;
3. export the spans and scan every attribute/event for forbidden payload;
4. confirm error spans carry an exception event holding `exception.type` alone,
   with no message, no stack trace and no status description. The event itself
   survives: `ExceptionSanitizingSpanExporter` strips the attributes and clears
   the description rather than dropping the event, so the exception type stays
   available for triage while the text does not leave the process. An earlier
   version of this gate demanded no exception event at all, which no code
   enforced and which would have failed a correct export; and
5. attach the sanitized export to release evidence.

`WholeExportAllowlistTests` executes steps 3 and 4 against the sanitizing
exporter in the position it occupies in production, on both a success and a
failure path, and includes a case proving the gate fails when an unmodelled
attribute is exported. Steps 1, 2 and 5 remain manual against a real collector.

## Oracle And RAGAS

The reference checkout must be LightRAG `v1.5.4` commit
`9a45b64c2ee25b1d806e90db926a8af37480bb16`. Use identical sanitized corpus,
recorded model responses, embedding profile and query set on both runtimes.
Compare normalized entities, relations, keywords, retrieval channel membership,
references, ordering invariants and token-allocation decisions. Do not compare
provider prose byte-for-byte.

Regenerate the deterministic checked-in oracle and run its Java consumer from
the repository root:

```powershell
evaluation\.venv\Scripts\python.exe `
  evaluation\oracle\generate_lightrag_v1_5_4.py `
  --upstream D:\OrgMemory\tmp\upstream-lightrag-v1.5.4 `
  --output evaluation\baselines\lightrag-v1.5.4-oracle.json

.\gradlew.bat :components:graph-rag-core:test `
  --tests "com.orgmemory.graphrag.query.LightRagUpstreamOracleTests"
```

The generator verifies the upstream Git commit before executing upstream code.
The committed JSON is the only oracle copy consumed by Java tests; a changed
fixture must be reviewed as a semantic change.

RAGAS is an external evaluation tool, never a runtime dependency. Pin its
environment and judge model, repeat the baseline to measure judge variance,
then fail only on a committed regression tolerance for faithfulness, context
precision and context recall. Never report a single stochastic judge run as an
absolute quality score.

## Adapter Load Comparison

PostgreSQL, OpenSearch and Neo4j comparisons are valid only when all runs use:

- the same canonical corpus and authorization scopes;
- the same immutable embeddings and query set;
- pinned server/client versions and identical hardware limits;
- separate cold and warm phases;
- at least five measured repetitions after warm-up; and
- p50/p95 latency, throughput, error rate and recall-at-k together.

A lower latency with lower recall is not a winner. PostgreSQL remains the
canonical evidence/ACL/publication authority regardless of which rebuildable
query adapter wins a workload.

## Security And Failure Drills

Run and retain evidence for:

1. cross-tenant retrieval, graph, citation and export denial;
2. a denied contribution that shares an entity with an allowed contribution;
3. OpenFGA outage before retrieval and before citation open;
4. PostgreSQL, OpenSearch and Neo4j query-store outages;
5. deletion followed by rebuild, proving retired evidence is absent from
   content, lexical, vector, graph and citation resolution; and
6. authorization generation/model changes during retrieval.

Every unavailable authorization authority fails closed. A query accelerator may
fall back only to another path that applies the same pinned tenant, snapshot and
authorized-asset filters.

## Backup And Restore Boundary

Back up the canonical PostgreSQL `orgmemory` database, the PostgreSQL `openfga`
database, and the versioned object-storage bucket together. Keycloak realm
configuration is versioned in the repository; production identity state still
follows the identity provider's supported backup procedure. OpenSearch and
Neo4j are rebuildable projections and must not be the only copy of evidence or
ACL state.

A restore drill passes only when:

1. database and object snapshots come from the same recorded backup window;
2. Flyway validation succeeds without repair;
3. evidence blob hashes match the canonical ledger;
4. OpenFGA model and tuples match the recorded authorization version;
5. projections rebuild into a new generation;
6. allow, deny, revoke and cross-tenant tests pass after restore; and
7. the old projection generation never becomes visible during rebuild.

Record recovery point and recovery time from the executed drill. Do not claim
backup/restore readiness from this procedure alone.
