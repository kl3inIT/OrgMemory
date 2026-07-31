# Spring Modulith Package Refactor Plan

- [x] Inventory Java files and external consumers in `knowledge` and
      `assetregistry`.
- [x] Challenge the internal-boundary strategy with Claude Fable 5 and record
      the judged decision.
- [x] Deliver `knowledge::storage` plus the `knowledge.space` nested module,
      import repairs, focused tests, and verification in one code PR below 100
      changed files.
- [ ] Move the remaining Knowledge slices in coherent code PRs below 100 files,
      replacing cross-slice repository access with facades as each edge is
      encountered.
- [ ] Close every Knowledge nested module and declare its allowed dependencies.
- [ ] Move Asset Registry kernel, authorization, and profile families in
      coherent code PRs below 100 files.
- [ ] Close every Asset Registry nested module and declare its allowed
      dependencies.
- [ ] Reconcile the architecture/spec/test sources, verify zero `Type.OPEN`
      annotations remain, and archive the increment.

## First Pull Request Evidence

- `knowledge.space` is present as an open nested application module.
- `knowledge::storage` contains `ObjectStoragePort` and its contract types.
- Production and test code compile under the new fully qualified names.
- Focused Knowledge Space tests and `ModulithVerificationTests` pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop.

Local verification on 2026-07-31: all production and test sources compiled,
the focused Knowledge Space and Modulith tests passed, `:core:test` passed, and
the terminating repository-wide `clean test` completed successfully across 108
tasks. After merging current `origin/main`, a second `clean test` completed in
8m42s across the same 108 tasks and `python scripts/check_docs.py` passed. The
pull request contains 48 changed files.

## Second Pull Request Evidence

- Canonical source/revision, evidence blob, raw/normalized processing, upload,
  query, and ingestion-job types form the open `knowledge.sourceledger` nested
  module.
- Production and test code compile under the new fully qualified names.
- Focused source-ledger tests and `ModulithVerificationTests` pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop.

Local verification on 2026-07-31: all production and test sources compiled,
the focused source-ledger, connector, graph, citation, lifecycle, and Modulith
tests passed, `:core:test` passed, and the terminating repository-wide
`clean test` completed successfully in 6m56s across 99 tasks. The docs
operating-model check passed. After merging current `origin/main` at
`af8b701`, a second `clean test` completed in 7m10s across 99 tasks and the
docs check passed against 361 Markdown files. The pull request contains 89
changed files.

## Third Pull Request Evidence

- Source ACL snapshots/heads, external principals/mappings, and group
  membership evidence form the open `knowledge.acl` nested module.
- Production and test code compile under the new fully qualified names.
- Focused ACL/principal/membership tests and `ModulithVerificationTests` pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop.

Local verification on 2026-07-31: all production and test sources compiled;
focused ACL, principal, membership, connector, retrieval, graph, and Modulith
tests passed; `:core:test` passed; and the terminating repository-wide
`clean test` completed successfully in 6m46s across 108 tasks. The docs
operating-model check passed. The pull request contains 84 changed files.

## Fourth Pull Request Evidence

- Connector contracts, crawl batch state, source profiles and registries, and
  ingestion orchestration form the first half of the open
  `knowledge.connector` nested module.
- Production and test code compile under the new fully qualified names without
  widening package-private connector implementation details.
- Focused connector, adapter, worker, and `ModulithVerificationTests` pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the connector persistence/runtime half starts.

Local verification on 2026-08-01: all production and test sources compiled;
focused connector and Modulith tests passed; connector adapter tests and the
worker connector integration suite passed; `:core:test` passed; and the
terminating repository-wide `clean test` completed successfully in 5m55s
across 108 tasks. The docs operating-model and mechanical source checks passed.
The pull request contains 88 changed files.

## Fifth Pull Request Evidence

- Crawl attempt/checkpoint persistence, source connection administration and
  credentials, connector identity observations, and membership sync runs join
  the existing open `knowledge.connector` nested module.
- Production and test code compile under the new fully qualified names without
  widening additional implementation details.
- Focused connector, ACL, adapter, worker, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the next Knowledge slice starts.

Local verification on 2026-08-01: all production and test sources compiled;
focused asset, publication/convergence, graph-edge, retrieval-scope, catalog,
and Modulith tests passed; `:core:test` passed; and the terminating
repository-wide `clean test` completed successfully in 5m26s across 108 tasks.
The docs operating-model and mechanical source checks passed. The pull request
contains 59 changed files.

Local verification on 2026-08-01: all production and test sources compiled;
focused connector, ACL, API admin, worker checkpoint, adapter, and Modulith
tests passed; `:core:test` passed; and the terminating repository-wide
`clean test` completed successfully in 6m24s across 108 tasks. The docs
operating-model and mechanical source checks passed. The pull request contains
44 changed files.

## Sixth Pull Request Evidence

- Knowledge Asset aggregate/version/evidence, lifecycle and publication
  orchestration, authorization convergence, and chunk projection form the open
  `knowledge.asset` nested module.
- Catalog federation remains with the future retrieval slice instead of
  widening retrieval-scope internals for this move.
- Focused asset, publication/convergence, graph-edge, ingestion, and Modulith
  tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the next Knowledge slice starts.

Local verification on 2026-08-01: focused asset, publication/convergence,
graph-edge, ingestion, retrieval, catalog, vector-literal, and Modulith tests
passed; the terminating repository-wide `clean test` completed successfully;
the docs operating-model and mechanical source checks passed; and both
CodeRabbit findings were resolved. The pull request contains 60 changed files.

## Seventh Pull Request Evidence

- Graph-index jobs and claiming, processing profiles, lifecycle orchestration,
  curation, exploration, and export form the open `knowledge.graph` nested
  module.
- GraphRAG query retrieval remains with the future retrieval slice.
- Compiler-forced embedding-profile and retrieval-scope visibility is tracked
  as temporary edge debt, while a structural allowlist prevents the graph
  module's open boundary from gaining consumers or deeper internal coupling.
- Focused graph, retrieval-regression, worker, API, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the retrieval slice starts.

Local verification on 2026-08-01: production and test sources compiled;
focused graph, retrieval-regression, API, worker, and Modulith tests passed;
`:core:test` passed; and the terminating repository-wide `clean test`
completed successfully in 5m13s across 108 tasks. The docs operating-model
and mechanical source checks passed. The pull request contains 56 changed
files. After merging current `origin/main` at `e088e9c9`, a second `clean test`
completed successfully in 6m18s across 99 tasks and the docs check passed
against 378 Markdown files. After merging current `origin/main` again at
`f99afb9f`, resolving the retrieval-scope helper seam, and rerunning focused
tests, a third `clean test` completed successfully in 6m46s across 108 tasks.
The product release contract check passed with the required Tegami entry.
Review identified and removed the reciprocal Asset-to-Graph projection type
dependency; Asset now returns a graph-neutral projection that Graph maps at its
own boundary. Focused and core tests passed after that repair, and the final
repository-wide `clean test` completed successfully in 6m26s across 99 tasks.
PR #190 merged as `7a970969ff51e25f5db75a9619fddad64198f4bb` after all
required CI checks and the CodeRabbit review passed.

## Eighth Pull Request Evidence

- Query embedding contracts, embedding profiles and registry, projection
  namespaces, and embedding configuration form the first half of the open
  `knowledge.retrieval` nested module.
- Runtime search, authorization rechecks, evidence/citation assembly, and
  retrieval persistence remain for the next retrieval code pull request.
- A structural allowlist pins the exact consumers and internal contract types
  exposed while the retrieval module is temporarily open.
- Focused retrieval, graph, source-ledger, API, worker, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the retrieval runtime slice starts.

Local verification on 2026-08-01: production and test sources compiled;
focused retrieval, graph, source-ledger, API, worker, and Modulith tests
passed; `:core:test` passed; and the terminating repository-wide `clean test`
completed successfully in 5m15s across 108 tasks. The docs operating-model,
release contract, and mechanical source checks passed. The pre-stage worktree
contains 55 changed paths.
After merging current `origin/main` at `95b71730`, a second repository-wide
`clean test` completed successfully in 6m22s across 99 tasks; the PR diff
remained 55 changed files.

PR #193 merged as `1535aa1338c22283816c1da9dd847102b8750bb4` after all
required CI checks passed. CodeRabbit's one actionable Markdown heading
finding was fixed and its review thread resolved before merge.

## Ninth Pull Request Evidence

- Authorized hybrid search, canonical authorization rechecks, evidence-scope
  resolution, catalog federation, citation streaming, GraphRAG result
  assembly, retrieval policy/configuration, and persistence join the existing
  open `knowledge.retrieval` nested module.
- Production and test code compile under the new fully qualified names without
  widening package-private implementation details.
- The structural allowlist pins the complete runtime boundary's exact core
  consumers and exposed internal retrieval types.
- Focused retrieval, Assistant, graph, source-ledger, API, worker, and Modulith
  tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before Knowledge boundary closing starts.

Local verification on 2026-08-01: production and test sources compiled;
focused retrieval and Modulith tests passed; affected API and worker
integration tests passed; `:core:test` passed; and the terminating
repository-wide `clean test` completed successfully in 6m18s across 108 tasks.
The docs operating-model, release contract, and mechanical source checks
passed. The pre-stage worktree contains 94 changed paths.

PR #194 merged as `f7831eb328e8910485da393955edb9e6134f368a` after all
required CI checks passed. CodeRabbit was rate limited on the final run, and
direct inspection confirmed zero inline comments, reviews, or review threads
before merge.

## Tenth Pull Request Evidence

- `SourceGroupView` belongs to `knowledge.acl`, `SourceIdentityTrust` belongs
  to `knowledge.connector`, and `SourceFailureMessage` belongs to
  `knowledge.sourceledger`.
- The parent `knowledge` package contains no domain type, and a structural test
  prevents types from accumulating there again.
- The close-all probe is recorded as dependency evidence; modules remain open
  until reciprocal ACL/Connector, Source Ledger, Asset/Retrieval, and Graph
  seams are replaced rather than hidden in `allowedDependencies`.
- Focused ACL, Connector, Source Ledger, Asset, Graph, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the first cycle-removal slice starts.

Local verification on 2026-08-01: production and test sources compiled;
`ModulithVerificationTests`, the full `:core:test` suite, and affected API
connector/admin integration tests passed; and the terminating repository-wide
`clean test` completed successfully in 5m17s across 108 tasks. The docs
operating-model, release contract, and mechanical source checks passed. The
pre-stage worktree contains 22 changed paths and zero Knowledge root domain
types.

PR #196 merged as `fdfb977129027bc7dcff37be4c98c8be92f40718` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Eleventh Pull Request Evidence

- ACL owns membership sync provenance, capture state, identity observations,
  resolved-principal commands, and its connection-principal summary query.
- Connector maps crawl DTOs into ACL commands and owns connection trust,
  configuration, credential, and crawl administration.
- Production and test code under `knowledge.acl` have zero imports from
  `knowledge.connector`; an ArchUnit rule prevents that edge from returning.
- Existing connection configuration, credential, crawl, trust, principal
  mapping, and group membership behavior remains covered by focused tests.
- `:core:test`, affected API/worker integration tests, and the repository
  terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the next Knowledge cycle-removal slice starts.

Local verification on 2026-08-01: production and test sources compiled;
focused ACL, Connector, API, Worker, and Modulith tests passed; and the
terminating repository-wide `clean test` completed successfully in 6m52s
across 108 tasks. The docs operating-model, release contract, diff hygiene,
and zero ACL-to-Connector import checks passed. The pre-stage worktree
contains 31 changed paths.

After merging current `origin/main` at `26c4f68f`, a second repository-wide
`clean test` completed successfully in 4m26s across 99 tasks; the PR diff
remained 31 changed files.

PR #198 merged as `827a16dc7c0b98b6f9f882d35c5149fd0e1c42af` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Twelfth Pull Request Evidence

- Source Ledger owns visibility and embedding-profile ports plus the compact
  profile ref persisted on revision completion.
- Retrieval implements authorization-aware source visibility and profile
  metadata lookup without exposing retrieval implementation types upstream.
- Production and test code under `knowledge.sourceledger` have zero imports
  from `knowledge.retrieval`; an ArchUnit rule prevents that edge returning.
- The shared opaque knowledge-resource not-found error no longer makes ACL,
  Space, Graph, or Source Ledger depend on Retrieval.
- Focused source query, retrieval adapter, API, Worker, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the Source Ledger-to-Asset slice starts.

Local verification on 2026-08-01: production and test sources compiled;
focused source-query, secure-visibility, API, Worker, and Modulith tests
passed; and the terminating repository-wide `clean test` completed
successfully in 7m22s across 108 tasks. Mechanical checks confirm zero Source
Ledger-to-Retrieval imports and zero stale not-found exception references. The
pre-stage worktree contains 28 changed paths.

After merging current `origin/main` at `78b4d503`, a second repository-wide
`clean test` completed successfully in 12s across 99 tasks with the shared
build cache; the PR diff remained 28 changed files.

PR #200 merged as `80123f5f1dc595b68bd848f9a1a83c195a01a049` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Thirteenth Pull Request Evidence

- Source Ledger owns the asset-promotion port, request, and provenance ref;
  Asset implements persistence behind that outbound boundary.
- Asset owns version retirement directly, so deletion no longer calls back
  into Source Ledger for Asset state changes.
- Evidence content-type policy belongs to Source Ledger, while Graph enqueue
  accepts stable Asset/version IDs and validates Asset state internally.
- Production and test code under `knowledge.sourceledger` have zero imports
  from `knowledge.asset`; an ArchUnit rule prevents that edge returning.
- Focused Source Ledger, Asset, Graph, API, Worker, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the Source Ledger-to-Space slice starts.

Local verification on 2026-08-01: production and test sources compiled;
focused Source Ledger, Asset, Graph, API, Worker, and Modulith tests passed;
`:core:test` passed; and the terminating repository-wide `clean test`
completed successfully in 5m35s across 108 tasks. The docs operating-model,
release contract, diff hygiene, and zero Source Ledger-to-Asset import checks
passed. The pre-stage worktree contains 24 changed paths.

After merging current `origin/main` at `250e1705`, a second repository-wide
`clean test` completed successfully in 6m53s across 108 tasks; the PR diff
remained 24 changed files.

PR #202 merged as `be73bf7e7d15bc65cf7d77dbc8e3b81f34764736` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Fourteenth Pull Request Evidence

- Source Ledger owns the upload-target and organization-membership port plus
  the compact Space facts required to persist source provenance.
- Space implements authorization and directory lookup behind that port,
  retaining its existing permission and active-space policy.
- Production and test code under `knowledge.sourceledger` have zero imports
  from `knowledge.space`; an ArchUnit rule prevents that edge returning.
- Focused upload, promotion, Space, API, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the remaining Knowledge boundaries are closed.

Local verification on 2026-08-01: production and test sources compiled;
focused upload, promotion, Space, API, and Modulith tests passed; `:core:test`
passed; and the terminating repository-wide `clean test` completed
successfully in 5m14s across 108 tasks. The docs operating-model, release
contract, diff hygiene, and zero Source Ledger-to-Space import checks passed.
The pre-stage worktree contains 11 changed paths.

After merging current `origin/main` at `9ff6f6d7`, a second repository-wide
`clean test` completed successfully in 9s across 99 tasks with the shared
build cache; the PR diff remained 11 changed files.

PR #205 merged as `7b69132897a69324252e09effe108c063d254389` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Fifteenth Pull Request Evidence

- The current source head view belongs to Source Ledger and exposes only the
  identity, ACL generation, and content revision needed by Connector.
- Connector consumes `findSourceHead` without making Source Ledger return a
  Connector-owned projection type.
- Production and test code under `knowledge.sourceledger` have zero imports
  from `knowledge.connector`; an ArchUnit rule prevents that edge returning.
- Focused Connector, Source Ledger, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the Source Ledger-to-Graph seam is removed.

Local verification on 2026-08-01: production and test sources compiled;
focused Connector, Source Ledger, Worker, and Modulith tests passed;
`:core:test` passed; and the terminating repository-wide `clean test`
completed successfully in 4m56s across 108 tasks. The docs operating-model,
release contract, diff hygiene, and zero Source Ledger-to-Connector import
checks passed. The pre-stage worktree contains 8 changed paths.

PR #206 merged as `0f0ebd4438c83257c2810d5ad2c3f1356a3b4f06` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Current Pull Request Gates

- Source Ledger owns the graph-index scheduling port used after source
  publication reaches READY.
- Graph implements that port and keeps Asset validation, profile resolution,
  idempotency-key construction, and durable queue persistence inside Graph.
- Production and test code under `knowledge.sourceledger` have zero imports
  from `knowledge.graph`; an ArchUnit rule prevents that edge returning.
- The temporary Graph consumer allowlist shrinks to Connector alone.
- Focused Source Ledger, Graph, Worker, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before Source Ledger's ACL boundary is addressed.

Pre-PR verification completed: production and test compilation across Core,
API, and Worker passed; focused Source Ledger pipeline, Graph lifecycle,
Graph coordinator, and Modulith tests passed; `:core:test` passed; the docs
operating-model check passed across 396 Markdown files and 8 mirrored domain
pairs; all 37 release-policy tests passed; and the terminating repository
`clean test` gate completed successfully in 4m55s across 108 tasks. Diff
hygiene and the zero Source Ledger-to-Graph import check passed. The pre-stage
worktree contains 7 changed paths.
