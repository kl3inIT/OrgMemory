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

## Sixteenth Pull Request Evidence

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

PR #207 merged as `109d03a3a367f1e8d0f7008b2af04139fff24ddf` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Seventeenth Pull Request Evidence

- ACL owns the compact source target value used to create and advance an ACL
  head; it no longer accepts a Source Ledger persistence entity.
- ACL generation conflicts preserve the existing conflict category and stable
  `knowledge-ingestion.conflict` code without importing the Source Ledger
  exception type.
- Production and test code under `knowledge.acl` have zero imports from
  `knowledge.sourceledger`; an ArchUnit rule prevents that edge returning.
- Focused ACL head, Source Ledger query, and Modulith tests pass.
- `:core:test` and the repository terminating `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the reverse Source Ledger-to-ACL boundary is
  addressed.

Pre-PR verification completed: production and test sources across Core, API,
and Worker compiled; focused ACL head, Source Ledger query, and Modulith tests
passed in 42s; `:core:test` passed in 1m17s; the docs operating-model check
passed across 397 Markdown files and 8 mirrored domain pairs; all 37
release-policy tests passed; and the terminating repository `clean test` gate
completed successfully in 4m55s across 108 tasks. Diff hygiene and the zero
ACL-to-Source Ledger import check passed. The pre-stage worktree contains 12
changed paths.

CodeRabbit identified that independently supplied ACL targets and snapshots
also needed an explicit organization/raw-source identity match. The constructor
and advance path now reject both mismatch classes before mutating the head;
four focused mismatch cases passed, and the terminating repository `clean
test` gate passed again in 5m28s across 99 tasks.

PR #208 merged as `a47c6e278e2c370797d50855ffe310715531d5aa` after all
required CI checks passed. Its actionable CodeRabbit finding was fixed in
`fc8b3be5`, confirmed by the reviewer, and the only review thread was resolved
before merge.

## Eighteenth Pull Request Evidence

- ACL owns a transactional facade for validation, snapshot/entry/seal
  persistence, head advancement, and normalization/promotion readiness.
- Source Ledger retains the raw-source identity lock but has zero dependencies
  on ACL repositories or JPA entities.
- ACL exposes immutable snapshot/head facts rather than persistence objects.
- An exact Modulith assertion pins the ACL contracts consumed by Source Ledger
  so the boundary cannot widen silently.
- Focused ACL facade/head, ingestion API, connector, worker, and Modulith tests
  pass; `:core:test` and the terminating repository `clean test` gate pass.
- The pull request changes fewer than 100 files and completes the normal
  CI/review/merge loop before the remaining ACL value-type seam is assessed.

Pre-PR verification completed: Core, API, and Worker production/test sources
compiled; focused ACL facade/head, ingestion API, Connector, Worker, and
Modulith tests passed; the full `:core:test` plus ingestion API/Worker suite
passed in 1m37s; the docs operating-model check passed across 398 Markdown
files and 8 mirrored domain pairs; all 37 release-policy tests passed; and the
terminating repository `clean test` gate completed successfully in 5m46s
across 108 tasks. Diff hygiene, the zero Source Ledger-to-ACL-persistence scan,
and the 12-path PR limit check passed.

PR #209 merged as `daeeb75adf3a6396522778ef9f5e7a7c83854935` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Nineteenth Pull Request Evidence

- `knowledge.sourceledger` is a closed nested application module rather than
  an open migration module.
- Its outgoing allowlist is limited to `knowledge.acl`, `knowledge::storage`,
  organization, permission, shared, and `shared::error`.
- `modules.verify()` passes, proving current consumers use only Source Ledger's
  public module surface and no undeclared outgoing edge exists.
- Focused Modulith and Source Ledger consumer tests pass; `:core:test` and the
  terminating repository `clean test` gate pass.
- The pull request contains production module metadata and tests, remains below
  100 files, and completes CI/review/merge before the next Knowledge module is
  assessed for closure.

Pre-PR verification completed: the initial closed-module probe passed; the
explicit outgoing allowlist passed `modules.verify()`; focused Source Ledger,
Connector, API, Worker, and Modulith tests passed in 1m47s; `:core:test` passed
in 1m24s; the docs operating-model check passed across 399 Markdown files and
8 mirrored domain pairs; all 37 release-policy tests passed; and the
terminating repository `clean test` gate completed successfully in 6m30s
across 108 tasks. Diff hygiene and the under-100-file scope check passed.

CodeRabbit requested that the regression test pin the exact outgoing allowlist
and that the release note link the existing independent architecture
challenge. Both review findings are addressed without rerunning the settled
challenge or changing the closure outcome. The focused Modulith, docs, and
release gates passed, and the terminating repository `clean test` gate passed
again in 1m23s across 99 tasks using the shared build cache.

PR #210 merged as `4feaf5ca3a89b254c57932d2441747ced5c56b04` after all
required CI checks passed. CodeRabbit confirmed both fixes and resolved both
review threads before merge.

## Twentieth Pull Request Evidence

- ACL owns a read-only `SourceAclQuery` and immutable snapshot/Space-generation
  facts for sibling consumers.
- Retrieval and Graph no longer inject `SourceAclSnapshotRepository`; Graph no
  longer consumes the `SourceAclSnapshot` JPA entity.
- ACL has zero imports from Space, and the cross-owned
  `KnowledgeSpaceAclGeneration` projection is removed.
- Exact Modulith assertions pin the ACL contracts consumed by Retrieval and
  Graph so persistence types cannot leak back across the boundary.
- Focused ACL query, Retrieval, Graph, and Modulith tests pass; `:core:test` and
  the terminating repository `clean test` gate pass.
- The pull request contains production code and remains below 100 changed
  files before the ACL module-closing cycle begins.

Pre-PR verification completed: focused ACL query, Retrieval, Graph, and
Modulith tests passed; `:core:test` passed in 1m15s; the docs operating-model
check passed across 400 Markdown files and 8 mirrored domain pairs; all 37
release-policy tests passed under Node 24.15; and the terminating repository
`clean test` gate completed successfully in 5m10s across 108 tasks.

After merging current `origin/main` at `8f644113`, the docs check passed across
404 Markdown files, all 37 release-policy tests passed again, the PR diff
remained 16 paths, and a second terminating `clean test` completed successfully
in 6m08s across 99 tasks.

After merging current `origin/main` again at `acd2b48f`, including the
authorization-consolidation changes, focused boundary tests passed in 46s, the
docs check passed across 415 Markdown files, all 37 release-policy tests passed,
and the terminating `clean test` completed successfully in 5m53s across 99
tasks. The PR diff remains 16 paths.

PR #213 merged as `6e8e5fe2d357f217d9c0bf16716576046a2bba8e` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Twenty-First Pull Request Evidence

- `knowledge.acl` is a closed nested application module rather than an open
  migration module.
- Its outgoing allowlist is limited to organization, permission, shared, and
  `shared::error`.
- `modules.verify()` passes, proving existing consumers use only ACL's public
  root-package contracts and no undeclared outgoing edge exists.
- The closure regression test pins both the closed state and the exact
  four-entry dependency allowlist.
- Focused ACL and Modulith tests, `:core:test`, docs/release checks, and the
  terminating repository `clean test` gate pass.
- The pull request contains production module metadata and tests and remains
  below 100 changed files before the next Knowledge module is assessed.

Pre-PR verification completed: focused ACL and Modulith tests passed in 25s;
`:core:test` passed in 1m08s; the docs operating-model check passed across 417
Markdown files and 8 mirrored domain pairs; all 37 release-policy tests passed
under Node 24.15; and the terminating repository `clean test` gate completed
successfully in 5m07s across 99 tasks. Diff hygiene, the zero ACL sibling-import
scan, and the four-path PR scope check passed.

CodeRabbit requested that the release note spell out `shared::error` instead
of grouping it under vague shared-foundation wording. The note now mirrors the
exact four-entry allowlist already enforced by production metadata and tests.

PR #216 merged as `b6a821c2eee5b8bb2b7a626a3758e975c372fa90` after all
required CI checks passed. CodeRabbit confirmed the release-note correction
and resolved its only review thread before merge.

## Twenty-Second Pull Request Evidence

- Space owns a read-only `KnowledgeSpaceQuery` for tenant-scoped existence and
  active-availability checks.
- Graph and Retrieval no longer inject `KnowledgeSpaceRepository` or consume
  Space persistence directly.
- Exact Modulith assertions pin `KnowledgeSpaceQuery` as the only Space type
  consumed by Graph and Retrieval, and an ArchUnit rule rejects repository
  dependencies from either module.
- Existing Graph curation, exploration, export, and authorization-resource
  behavior remains covered by focused and full Core tests.
- Focused Space, Graph, and Modulith tests, `:core:test`, docs/release checks,
  and the terminating repository `clean test` gate pass.
- The pull request contains production code and remains below 100 changed
  files before the Space module-closing cycle begins.

Pre-PR verification completed: focused Space and Graph tests passed in 29s;
the exact Modulith boundary suite passed in 19s; `:core:test` passed in 1m27s;
the docs operating-model check passed across 418 Markdown files and 8 mirrored
domain pairs; all 37 release-policy tests passed under Node 24.15; and the
terminating repository `clean test` gate completed successfully in 5m36s
across 99 tasks. Diff hygiene, zero sibling Space-repository imports, and the
14-path PR scope check passed.

PR #217 merged as `309f451a383920c18403ae362dd7d460bcd6e2ff` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Twenty-Third Pull Request Evidence

- `knowledge.space` is a closed nested application module rather than an open
  migration module.
- Its outgoing allowlist is limited to authorization, Source Ledger,
  organization, permission, shared, and `shared::error`.
- `modules.verify()` passes, proving current consumers use only Space's public
  root-package contracts and no undeclared outgoing edge exists.
- The closure regression test pins both the closed state and the exact
  six-entry dependency allowlist.
- Focused Space and Modulith tests, `:core:test`, docs/release checks, and the
  terminating repository `clean test` gate pass.
- The pull request contains production module metadata and tests and remains
  below 100 changed files before the next Knowledge module is assessed.

The initial closure probe surfaced the existing Space administration dependency
on permission-audit contracts. `permission` was added to both the production
allowlist and exact regression assertion; `modules.verify()` then passed in
21s. Full `:core:test` passed in 1m11s; the docs operating-model check passed
across 419 Markdown files and 8 mirrored domain pairs; all 37 release-policy
tests passed under Node 24.15; and the terminating repository `clean test` gate
completed successfully in 4m56s across 99 tasks. Diff hygiene and the four-path
PR scope check passed.

After merging current `origin/main` at `fa033f51`, the focused Modulith
verification passed in 7s. The docs operating-model check passed across 426
Markdown files and 8 mirrored domain pairs; all 37 release-policy tests passed
under Node 24.15; and the terminating repository-wide `clean test` completed
successfully in 4m33s across 99 tasks. The final pull-request scope remains 4
changed paths.

PR #220 merged as `285261b951b25838c08cea91fb8960e010dffc61` after all
required CI checks passed. CodeRabbit was rate limited, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Twenty-Fourth Pull Request Evidence

- Asset owns `KnowledgeAssetGraphQuery` and immutable asset, version, and chunk
  facts for Knowledge Graph consumers.
- Graph indexing, queueing, lifecycle, and curation no longer inject Asset
  repositories, consume Asset JPA entities/status, or read the chunk projection
  store directly.
- Exact Modulith assertions pin the four Asset contracts consumed by Graph, and
  an ArchUnit rule prevents Asset persistence types from leaking back.
- Existing graph target validation, current-version checks, chunk loading, and
  opaque not-found behavior remain covered by focused tests.
- Focused Asset query, Graph, and Modulith tests, `:core:test`, docs/release
  checks, and the terminating repository `clean test` gate pass.
- The pull request contains production code and remains below 100 changed files
  before the remaining Graph dependency seams are assessed.

Pre-PR verification completed: focused Asset query, Graph, and exact Modulith
boundary tests passed in 21s; `:core:test` passed in 1m12s; the docs
operating-model check passed across 427 Markdown files and 8 mirrored domain
pairs; all 37 release-policy tests passed under Node 24.15; and the terminating
repository-wide `clean test` completed successfully in 4m57s across 99 tasks.
Diff hygiene, the zero Graph-to-Asset-persistence import scan, and the 16-path
PR scope check passed.

PR #221 merged as `5849afb2` after all
required CI checks passed. CodeRabbit completed with no actionable comments,
and direct inspection confirmed zero inline comments, reviews, or review
threads before merge.

## Twenty-Fifth Pull Request Evidence

- Source Ledger owns `SourceGraphIndexQuery` and an immutable revision fact for
  Graph indexing.
- `GraphIndexingCoordinator` no longer injects `SourceRevisionRepository` or
  consumes the Source revision JPA entity/status enum.
- Exact Modulith assertions pin the five intentional Source Ledger contracts
  consumed by Graph, and an ArchUnit rule rejects revision persistence leakage.
- Current/superseded graph-input behavior and tenant-scoped revision mapping
  remain covered by focused tests.
- Focused Source query, Graph coordinator, and Modulith tests, `:core:test`,
  docs/release checks, and the terminating repository `clean test` gate pass.
- The pull request contains production code and remains below 100 changed files
  before the remaining Graph-to-Retrieval seam is assessed.

Pre-PR verification completed: focused Source graph query, Graph coordinator,
and exact Modulith boundary tests passed in 31s; `:core:test` passed in 1m07s;
the docs operating-model check passed across 428 Markdown files and 8 mirrored
domain pairs; all 37 release-policy tests passed under Node 24.15; and the
terminating repository-wide `clean test` completed successfully in 4m51s
across 99 tasks. Diff hygiene, the zero Graph-to-Source-persistence import scan,
and the 9-path PR scope check passed.

PR #222 merged as `e2a67bb3` after all required CI checks passed. A full merge
SHA in the increment triggered a Gitleaks false positive, so the branch was
rewritten to use the documented short SHA; the replacement CI run passed.
CodeRabbit was rate limited, and direct inspection confirmed zero inline
comments, reviews, or review threads before merge.

## Twenty-Sixth Pull Request Evidence

- Retrieval's `EmbeddingProfileRegistry` exposes a tenant-scoped optional
  profile lookup for Graph indexing.
- `GraphIndexingCoordinator` no longer injects `EmbeddingProfileRepository` or
  consumes the embedding-profile JPA entity.
- Exact Modulith assertions pin the nine intentional Retrieval contracts used
  by Graph, and an ArchUnit rule rejects profile persistence leakage.
- Existing missing-profile retry behavior and tenant-scoped registry mapping
  remain covered by focused tests.
- Focused profile registry, Graph coordinator, and Modulith tests, `:core:test`,
  docs/release checks, and the terminating repository `clean test` gate pass.
- The pull request contains production code and remains below 100 changed files
  before the Graph module-closing cycle begins.

Pre-PR verification completed: focused embedding-profile registry, Graph
coordinator, and exact Modulith boundary tests passed in 20s; `:core:test`
passed in 1m12s; the docs operating-model check passed across 429 Markdown
files and 8 mirrored domain pairs; all 37 release-policy tests passed under
Node 24.15; and the terminating repository-wide `clean test` completed
successfully in 4m52s across 99 tasks. Diff hygiene, the zero
Graph-to-Retrieval-profile-persistence import scan, and the 9-path PR scope
check passed.

PR #223 merged as `0cb8a187` after all required CI checks passed. CodeRabbit
was rate limited, and direct inspection confirmed zero inline comments,
reviews, or review threads before merge.

## Twenty-Seventh Pull Request Evidence

- `knowledge.graph` is a closed nested application module rather than an open
  migration module.
- Its outgoing allowlist is limited to AI routing, authorization, ACL, Asset,
  Retrieval, Source Ledger, Space, organization, permission, shared, and
  `shared::error`.
- `modules.verify()` passes, proving current consumers use only Graph's public
  root-package contracts and no undeclared outgoing edge exists.
- The closure regression test pins both the closed state and the exact
  eleven-entry dependency allowlist.
- Focused `ModulithVerificationTests` passed in 28s. A clean Core test rerun
  passed in 1m23s after removing a stale concurrent test-result artifact.
- The documentation operating-model check passed across 430 Markdown files
  and 8 mirrored domain pairs; the Node 24.15 release gate passed all 37 tests.
- The terminating repository-wide `clean test` passed in 5m49s across 99
  tasks. Diff hygiene and the 4-path pull-request scope check passed.

PR #226 merged as `1a1515b6` after all required CI checks passed. CodeRabbit
was rate limited, and direct inspection confirmed zero inline comments,
reviews, or review threads before merge.

## Twenty-Eighth Pull Request Evidence

- Source Ledger owns `SourceInventoryQuery` and an immutable inventory summary
  for Connector read-side consumers.
- `ConnectorObjectDirectory` and `SourceConnectionActivityService` no longer
  inject `SourceObjectRepository` or consume Source Ledger status and aggregate
  persistence projections.
- An exact Modulith assertion pins the two Source Ledger contracts consumed by
  those read views, so persistence types cannot leak back into them.
- Existing active-object inventory, active/archived counts, and latest activity
  behavior remain covered by a focused Source Ledger query test.
- Focused Source Ledger query and Modulith tests, `:core:test`, docs/release
  checks, and the terminating repository `clean test` gate pass.
- The pull request contains production code and remains below 100 changed files
  before Connector's write-side Source Ledger seam is assessed.

Pre-PR verification completed: repository compilation passed in 33s; focused
Source Inventory and exact Modulith boundary tests passed in 29s; `:core:test`
passed in 1m27s; the documentation operating-model check passed across 434
Markdown files and 8 mirrored domain pairs; all 37 release-policy tests passed
under Node 24.15; and the terminating repository-wide `clean test` completed
successfully in 6m05s across 99 tasks. Mechanical package, zero-byte, and
migration-name checks, diff hygiene, the zero read-view persistence-leak scan,
and the 10-path pull-request scope check passed.

PR #227 merged as `ffa45f37` after all required CI checks passed. CodeRabbit
completed a full review with no actionable comments, and direct inspection
confirmed zero inline comments, reviews, or review threads before merge.

## Twenty-Ninth Pull Request Evidence

- Source Ledger exposes a stable `SourceInventoryRef` and owns
  `SourceLifecycleService` for canonical source retirement.
- `ConnectorReconciler` uses Source Ledger inventory/lifecycle APIs for source
  lookup, complete-crawl diffing, explicit tombstones, and retirement instead
  of its repository, entity, or status enum.
- The lifecycle command retains tenant/source/connection scoping and refuses to
  mutate missing or already archived sources; the returned inventory ref does
  not expose a stale lifecycle decision.
- An exact Modulith assertion pins the ten intentional Source Ledger contracts
  consumed by `ConnectorReconciler` so persistence types cannot leak back.
- Focused Source Inventory/Lifecycle, Connector edit/pruning integration, and
  Modulith tests, `:core:test`, docs/release checks, and the terminating
  repository `clean test` gate pass.
- The pull request contains production code and remains below 100 changed files
  before Connector revision staging/completion is moved behind Source Ledger's
  public API.

Pre-PR verification completed: repository compilation passed in 14s; focused
Source Inventory/Lifecycle and exact Modulith boundary tests passed in 28s;
Connector content-edit and pruning integration tests passed in 1m33s;
`:core:test` passed in 1m23s; the documentation operating-model check passed
across 435 Markdown files and 8 mirrored domain pairs; all 37 release-policy
tests passed under Node 24.15; and the terminating repository-wide `clean test`
completed successfully in 6m01s across 99 tasks. Mechanical package,
zero-byte, and migration-name checks, diff hygiene, the zero Reconciler
persistence-leak scan, and the 11-path pull-request scope check passed.

PR #228 merged as `703ddb20` after all required CI checks passed. CodeRabbit
was rate limited, and direct inspection confirmed zero inline comments,
reviews, or review threads before merge.

## Thirtieth Pull Request Evidence

- Source Ledger owns revision lookup, evidence/revision staging, completion,
  and graph scheduling through `SourceRevisionService` plus owner-defined
  commands and immutable draft facts.
- All three revision phases retain `REQUIRES_NEW`; completion advances the
  source current revision and calls the existing Source Ledger-owned graph port
  inside the same transaction.
- `ConnectorSourceRevisionCoordinator` is now only a translation adapter from
  Connector, Asset, and embedding facts; it no longer injects Source Ledger
  repositories/entities or `GraphIndexJobQueue`.
- The duplicate Connector-owned revision draft is removed, exact Modulith
  assertions pin the nine revision contracts, and Graph has no direct Knowledge
  sibling consumers.
- Focused Source Revision/Modulith and vertical Connector staging/content-edit
  integration tests, `:core:test`, docs/release checks, and the terminating
  repository `clean test` gate pass.
- The pull request contains production code and remains below 100 changed files
  before Connector's direct Asset and Retrieval translation seams are assessed.

Pre-PR verification completed: repository compilation passed in 10s; focused
Source Revision and exact Modulith boundary tests passed in 28s; vertical
Connector staging/content-edit integration tests passed in 1m29s; `:core:test`
passed in 1m29s; the documentation operating-model check passed across 436
Markdown files and 8 mirrored domain pairs; all 37 release-policy tests passed
under Node 24.15; and the terminating repository-wide `clean test` completed
successfully in 6m27s across 99 tasks. Mechanical package, zero-byte, and
migration-name checks, diff hygiene, the zero Coordinator persistence/Graph
import scan, duplicate-draft removal scan, and the 13-path pull-request scope
check passed.

PR #229 merged as `378d0518` after all required CI checks passed. CodeRabbit
was rate limited, and direct inspection confirmed zero inline comments,
reviews, or review threads before merge.

## Thirty-first Pull Request Evidence

- `knowledge.connector` is a closed nested application module rather than an
  open migration module.
- Its outgoing allowlist is limited to ACL, Asset, Retrieval, Source Ledger,
  Space, the Knowledge storage interface, organization, permission, shared,
  `shared::error`, and `shared::secret`.
- `modules.verify()` passes, proving Connector consumes only public contracts
  and has no undeclared outgoing edge or remaining module cycle.
- The closure regression test pins both the closed state and exact eleven-entry
  dependency allowlist.
- Focused Modulith tests, `:core:test`, docs/release checks, and the terminating
  repository `clean test` gate pass.
- The pull request contains production module metadata and tests and remains
  below 100 changed files before Asset and Retrieval are assessed.

Pre-PR verification completed: repository compilation passed in 9s; focused
`ModulithVerificationTests` passed in 33s; `:core:test` passed in 1m43s; the
documentation operating-model check passed across 437 Markdown files and 8
mirrored domain pairs; all 37 release-policy tests passed under Node 24.15;
and the terminating repository-wide `clean test` completed successfully across
99 tasks. Mechanical package, zero-byte, and migration-name checks, diff
hygiene, and the 5-path pull-request scope check passed.

CodeRabbit review then tightened the closure regression to read the exact
annotation declaration through Spring Modulith's `ApplicationModuleInformation`
rather than the effective dependency set. Connector's package contract now
also states why its unqualified Asset and Retrieval dependencies are temporary
and safe: every consumed type is in the owners' root API package and the
existing dependency tests pin that surface until those two modules close. The
review request for a new architecture record was rejected as duplication: this
closure directly applies the already judged Claude Fable 5 verdict in
[challenge-verdict.md](challenge-verdict.md), and introduces no new material
boundary decision.

Review-fix verification passed: focused `ModulithVerificationTests` in 29s,
`:core:test` in 1m30s, the 437-file docs check, all 37 release-policy tests
under Node 24.15, and a terminating repository `clean test` across 99 tasks.

PR #230 merged as `ddda1359` after all required CI checks passed. All three
CodeRabbit findings were verified: the exact declared dependency assertion and
temporary open-owner documentation were fixed, while the duplicate challenge
record request was answered with the existing Fable 5 verdict. Direct audit
confirmed all three review threads resolved before merge.

## Current Pull Request Gates

- The Source Ledger-owned promotion request carries every validated normalized
  fact needed to create an immutable Asset version; the Asset adapter no longer
  loads `NormalizedRecord` or `NormalizedRecordRepository`.
- `SourcePublicationService` owns source revision advancement and joins the
  existing Asset publication transaction with `Propagation.MANDATORY`, so the
  previous atomic asset/version/source/outbox commit is retained without Asset
  touching `SourceObject` or `SourceObjectRepository`.
- Asset maps the source request into its own `KnowledgeAssetVersionDraft`; its
  entity no longer accepts a Source Ledger persistence entity.
- A failing-first ArchUnit regression now rejects all four Source Ledger
  persistence types, an exact dependency test pins the nine remaining public
  contracts, and focused service tests pin the mandatory transaction boundary.
- API ingestion and Worker publication pipeline integration tests pass, and
  the pull request remains a coherent code change below 100 files.

Pre-PR verification completed: the new persistence-boundary test failed first
against all four direct entity/repository dependencies, then focused Source
Publication and exact Modulith tests passed in 34s. Vertical API ingestion and
Worker publication pipeline tests passed in 1m22s and 1m19s; the combined full
Core/API/Worker gate passed in 8m19s. Repository compilation passed in 12s; the
documentation operating-model check passed across 445 Markdown files and 8
mirrored domain pairs; all 37 release-policy tests passed under Node 24.15;
and the terminating repository-wide `clean test` completed successfully in
1m02s across 99 tasks. Mechanical package, zero-byte, migration-name, diff,
zero Asset-to-Source-Ledger-persistence-import, and 14-path scope checks passed.
