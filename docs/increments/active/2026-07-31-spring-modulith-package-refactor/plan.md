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

## Current Pull Request Gates

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
