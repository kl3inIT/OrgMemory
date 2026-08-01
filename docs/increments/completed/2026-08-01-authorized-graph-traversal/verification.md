# Authorized graph traversal coordinator verification

Date: 2026-08-01

## Delivered

- `AuthorizedGraphTraversal` is the sole core result producer for authorized
  multi-source graph expansion.
- `AuthorizedGraphTraversalSource` requires exact snapshot validation,
  authorized entity reads, and bounded incident-relation pages with exclusive
  canonical relation-UUID cursors.
- PostgreSQL, Neo4j, OpenSearch, and the in-memory testkit use the shared
  coordinator. `GraphStore` no longer exposes final-ID traversal.
- The PostgreSQL recursive CTE and OpenSearch PPL final-result implementations
  were removed from the authorized query path. Apache AGE remains only behind
  the separate topology-candidate port whose output requires relational
  evidence recheck.
- ADR 0027, the secure GraphRAG spec/test pair, architecture, and roadmap were
  reconciled to the implemented contract.

## Challenge and reference evidence

Claude review was unavailable because its quota was exhausted. With explicit
owner direction, two independent Codex Ultra sessions argued the coordinator
and default-method alternatives, rebutted each other, and a third fresh Ultra
session judged the self-contained debate. The judge selected the core-owned
coordinator and rejected backend final-result overrides.

Pinned LightRAG `v1.5.4` (`9a45b64c2ee25b1d806e90db926a8af37480bb16`)
confirmed that ordinary retrieval orchestration and backend-specific graph
exploration are separate concerns. OrgMemory adopts the orchestration boundary
but keeps authorization, immutable snapshot identity, completion, and ordering
as one product-owned core contract.

## Characterization-first evidence

Commit `b5f98da2` recorded unchanged-code differences before production edits:
PostgreSQL accepted zero limit and validated empty snapshots, Neo4j rejected
zero limit while validating empty snapshots, OpenSearch rejected zero limit but
returned early for empty seeds, and the in-memory double preserved its own seed
ordering. The graph-core, testkit, PostgreSQL, Neo4j, and focused OpenSearch
characterization gates passed before implementation.

Implementation commit `02f82223` centralized the policy and migrated all
sources. Test commit `e990852c` additionally pins cycles, disconnected nodes,
seed permutations, one global limit, and model-boundary self-loop rejection.

## Verification gates

- Shared affected-module gate:
  `./gradlew --no-daemon :components:graph-rag-core:test :components:graph-rag-testkit:test :integrations:graph-rag-postgres:test :integrations:graph-rag-neo4j:test :integrations:graph-rag-opensearch:test`
  — passed in 6m13s with 184 tests, zero failures/errors/skips before the final
  additional core edge-case test.
- Final focused traversal gate:
  `./gradlew --no-daemon :components:graph-rag-core:test --tests "*AuthorizedGraphTraversalTests"`
  — passed with 9 tests.
- Terminating full repository gate:
  `./gradlew --no-daemon clean test`
  — passed after the graph-affecting rebase in 5m59s; 99 actionable Gradle
  tasks and 1,114 tests across 237 XML result files, with zero failures,
  errors, or skips. The same gate also passed before that rebase in 11m52s.
- One intermediate post-rebase full run lost the `apps:worker` Gradle test
  executor to native-memory exhaustion. Its generated `hs_err` file identified
  an orphaned 3 GB Gradle daemon from the earlier clean gate; the 40 worker
  tests that had emitted XML had no assertion failure. After stopping the exact
  orphan process, `:apps:worker:test --rerun-tasks` passed 61/61 and the final
  full clean gate above passed. No production or test code was changed for the
  resource incident.
- `origin/main` then advanced to `2c6011cc` only in raw-source media-type
  acceptance, its migration, and production Compose. After the final disjoint
  rebase, graph core/testkit tests, all three edited adapter compilations, and
  `:core:test` passed together in 1m54s. The final advance to `df856ab8`
  changed only the docs application, its hydration increment, roadmap, and
  pnpm lockfile; rebasing it did not change JVM sources or the traversal diff.
- `git diff --check` passed. Mechanical scans found no PostgreSQL, Neo4j, or
  OpenSearch `GraphStore` final traversal implementation and no surviving
  OpenSearch PPL/frontier configuration symbols. The only adapter-main
  `expandEntityIds` is the separately documented Apache AGE topology-candidate
  port.
- `pnpm release:check` passed under Node 24.15.0 with the product-impacting
  Tegami patch entry for the traversal correction.

The session did not expose an IDE semantic-inspection tool. The substitute
evidence is clean compilation of every edited Java module in both focused and
full clean gates plus the symbol scans above.

## Performance boundary

No production-scale graph latency corpus is available in this increment, so no
latency claim is made and no unverified native accelerator was restored. The
production query contract remains one hop by default, page memory is bounded,
and the coordinator requires exact completion. Production-scale traversal
latency remains an operational evidence gap; if it misses an agreed SLO, the
follow-up must add an evidence-bearing accelerator consumed and rechecked by
core rather than another final-result override.
