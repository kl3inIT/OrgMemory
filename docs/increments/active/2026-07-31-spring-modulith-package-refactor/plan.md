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

## Thirty-second Pull Request Evidence

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

PR #233 merged as `9e2e7248` after all required CI checks passed. CodeRabbit
was rate limited, and direct inspection confirmed zero inline comments,
reviews, or review threads before merge.

## Thirty-third Pull Request Evidence

- `KnowledgeCatalogItem`, `KnowledgeTextChunk`, and `PgVectorLiteral` move from
  Retrieval to their Asset owner without changing their data shape or behavior.
- Asset persistence and assembly no longer depend on those Retrieval types;
  Retrieval, Asset Registry, API, and Worker consumers now import Asset-owned
  root contracts, making the dependency direction one way for these values.
- JPQL constructor projections target the new Asset FQN, vector parsing tests
  move with the utility, and catalog/chunk tests retain their previous coverage.
- A failing-first ArchUnit regression proves the three old Asset-to-Retrieval
  edges existed, then prevents them from returning; exact temporary-boundary
  assertions pin the reduced Retrieval consumer/type surface and expanded
  Asset-owned surface.
- Full Core/API/Worker tests pass and the code PR remains below 100 files before
  the remaining embedding-profile and projection-namespace seams are handled.

Pre-PR verification completed: the new ownership test failed first against the
three Retrieval-owned values, then repository compilation passed in 14s and
focused moved-value/catalog/chunk/Modulith tests passed in 29s. The combined
full Core/API/Worker gate passed in 5m48s; the documentation operating-model
check passed across 446 Markdown files and 8 mirrored domain pairs; all 37
release-policy tests passed under Node 24.15; and the terminating repository
`clean test` completed successfully in 58s across 99 tasks. Mechanical package,
zero-byte, migration-name, diff, zero old-Retrieval-value-import, and 25-path
scope checks passed.

After merging current `origin/main`, the focused moved-value and Modulith tests
passed again in 7s; the documentation check passed across 451 Markdown files;
all 37 release-policy tests passed again under Node 24.15; and the final PR diff
contains 21 paths because Git recognizes the four package moves as renames.

CodeRabbit review found that the ownership regression proved only absence of
the three legacy Retrieval names. The test now also asserts the exact three
Asset-owned class names, so deletion or relocation outside Asset fails alongside
any reintroduced legacy dependency.

Review-fix verification passed: the focused positive/negative ownership test in
25s, full `:core:test` in 1m43s, and the 451-file documentation check.

PR #235 merged as `c29cc75e` after all required CI checks passed. CodeRabbit's
single ownership-test finding was fixed and direct audit confirmed its only
review thread resolved before merge.

## Thirty-fourth Pull Request Evidence

- Asset owns the compact `KnowledgeEmbeddingProfileRef` needed to persist chunk
  projections; Connector and Worker translate Retrieval's richer profile at
  their orchestration boundary.
- `KnowledgeProjectionNamespaces` moves to Asset because the identifiers name
  Asset-owned catalog, chunk, authorization, and graph projections. Graph and
  Retrieval consume that owner-defined value one way.
- A failing-first ArchUnit regression proves the previous Asset-to-Retrieval
  dependency and now rejects any direct edge from Asset back to Retrieval.
- Exact temporary-boundary assertions remove four Asset consumers from
  Retrieval's incoming surface and pin the new Asset-owned contracts.
- Repository compilation and full Core/API/Worker tests pass, and this code PR
  remains below 100 files before Asset itself is closed in the next PR.

Pre-PR verification completed: the new Asset isolation test failed first on
the unchanged direct Retrieval edge, then all 40 focused Modulith tests passed
in 27s. Repository compilation passed in 17s, and the combined full
Core/API/Worker gate completed successfully in 7m07s. The documentation,
operating-model check passed across 452 Markdown files and 8 mirrored domain
pairs; all 37 release-policy tests passed under Node 24.15; and the terminating
repository-wide `clean test` completed successfully in 1m04s across 99 tasks.
Mechanical package, zero-byte, migration-name, diff-hygiene, zero
Asset-to-Retrieval-import, zero Asset-to-Asset-Registry-import, and 19-path
scope checks passed.

After merging current `origin/main`, all 40 Modulith tests passed again in 8s;
the documentation check passed across 459 Markdown files; and all 37
release-policy tests passed again under Node 24.15. The final PR diff contains
18 paths because Git recognizes the projection-namespace package move as a
rename.

PR #239 merged as `c67effe2` after all required CI checks passed. CodeRabbit
was rate limited, and direct inspection confirmed zero inline comments,
reviews, or review threads before merge.

## Thirty-fifth Pull Request Evidence

- `knowledge.asset` is closed with an exact six-entry outgoing dependency
  allowlist.
- Parent Knowledge exposes exactly `KnowledgeCatalogQuery` and
  `KnowledgeCatalogEntry` through `knowledge::catalog`; Asset Registry catalog
  consumers no longer import nested Asset or Retrieval catalog types.
- Retrieval retains canonical authorization and maps the Asset-owned
  persistence projection. Version-only lookup resolves scope first and queries
  only current active versions within the authorized Asset set.
- The API owns its response mapping and preserves the existing
  `KnowledgeCatalogItem` OpenAPI component and eight-field wire shape.
- The Fable 5 spend-limit failure, clean Codex fallback verdict, strongest
  counterargument, counterattack, final choice, and rejected alternative are
  recorded with the active increment.

Local verification so far: the authorization-order characterization test
failed first against the unchanged implementation; then all 50 focused catalog
and Modulith tests passed in 41s. The runtime OpenAPI contract test passed with
PostgreSQL/Testcontainers in 1m13s, and the combined full Core/API/Worker gate
passed in 6m25s. Mechanical package, zero-byte, migration-name, diff-hygiene,
zero Asset-Registry-to-Asset-import, and 16-path scope checks passed. The
documentation operating-model check passed across 461 Markdown files and 8
mirrored domain pairs; all 37 release-policy tests passed under Node 24.15; and
the terminating repository-wide `clean test` passed in 3m08s across 99 tasks.
Integration with current `origin/main` and PR CI/review remain before merge.

The first PR CI run passed Backend Java 25, Web Node 24, documentation,
evaluation, secret, and impact checks but failed the product-release policy
because the PR body omitted its required release disposition. The body now
records that this internal package-boundary refactor has no user-facing release
impact. A fresh synchronize run is required because rerunning the original
workflow preserves its original pull-request event payload.

After merging current `origin/main` at `a86e892e`, all 50 focused catalog and
Modulith tests passed again in 26s. The documentation check passed across 467
Markdown files and 8 mirrored domain pairs, and the expanded release gate from
main passed all 40 tests under Node 24.15. The spec/test reconciliation markers
now point at the merge commit containing both the catalog boundary and the
concurrent Skill CLI lifecycle changes.

PR #241 merged as `13697ff9` after Backend Java 25, Web Node 24,
documentation, evaluation, secret, impact, and corrected release-policy checks
passed. CodeRabbit remained rate limited; direct inspection confirmed zero
inline comments, reviews, or review threads before merge, and both the PR head
and merge commit are ancestors of current `origin/main`.

## Thirty-sixth Pull Request Evidence

- Parent Knowledge exposes exactly `PermissionAwareKnowledgeSearch`,
  `RetrievedKnowledgeEvidence`, `SecureKnowledgeSearchResult`, and
  `VerifiedKnowledgeGrounding` through `knowledge::search`.
- Assistant and Asset Registry top-level search consumers cross only that
  parent interface and cannot import the open Retrieval implementation package.
- Retrieval keeps both concrete engines, authorization, ranking, persistence,
  and the final verified-grounding construction; this PR changes package
  ownership without changing query behavior or the API wire contract.
- The Fable 5 monthly spend-limit failure and clean Codex ultra fallback
  architecture verdict are recorded. The accepted-with-changes verdict keeps
  `VerifiedKnowledgeGrounding` in the four-type interface so the final
  permission-verified model input is not duplicated or weakened.
- This is a code PR under 100 changed paths. Retrieval remains explicitly open;
  Asset, Organization, Source Ledger citation, and Graph verifier seams follow
  as separate code PRs.

Local verification so far: the exact-interface characterization test failed
first with `NoSuchElementException` before `knowledge::search` existed. Core
and API main/test compilation now passes. The first 92-test focused run passed
all search, GraphRAG, Prompt, and Assistant behavior tests and exposed two new
structural assertion defects; after correcting the expected transitive result
types and excluding test bytecode from the production ArchUnit rule, the full
Modulith verification class passed in 21s and the complete focused suite passed
in 33s. The runtime OpenAPI contract passed in 1m20s with a single-use Gradle
process. The documentation operating-model check passed across 469 Markdown
files and 8 mirrored domain pairs; all 40 release-policy tests passed under
Node 24.15. The combined full Core/API/Worker gate passed in 8m04s, and the
terminating repository-wide `clean test` passed in 1m06s across 99 tasks after
an orphaned test worker from an earlier daemon crash released its JAR locks.
Mechanical old-package, top-level implementation-import, zero-byte,
migration-scope, diff-hygiene, and 41-path scope checks passed.

After merging current `origin/main` at `142a11cc`, the complete focused suite
passed again in 39s. The documentation check passed across 473 Markdown files
and 8 mirrored domain pairs, and all 40 release-policy tests passed again under
Node 24.15.

PR #250 merged as `ce1a970b` after Backend Java 25, Web Node 24,
documentation, evaluation, secret, impact, release-preview, release-policy,
and aggregate CI checks passed. CodeRabbit was rate limited; direct audit found
zero reviews, inline comments, or review threads, and both the PR head and merge
commit are ancestors of current `origin/main`.

## Thirty-seventh Pull Request Evidence

- Asset owns `KnowledgeAssetRetrievalQuery` for tenant-scoped existence, active
  authorization scopes, and current active catalog projections.
- Its JPA implementation remains package-private inside the closed Asset module;
  Retrieval imports neither `KnowledgeAssetRepository` nor
  `KnowledgeAssetVersionRepository`.
- `AuthorizationResourceDirectory`, `KnowledgeEvidenceScopeResolver`, and
  `KnowledgeCatalogService` are the exact Retrieval consumers of the owner
  query. Existing immutable Asset scope/catalog projections retain their shapes.
- Empty authorized sets return empty without persistence access; tenant,
  archived-asset, current-version, and active-version predicates remain in the
  Asset repositories behind the query.
- This code PR remains below 100 changed paths. Retrieval stays open for the
  Organization, Source Ledger citation, Graph verifier, and adapter seams.

Local verification so far: the repository-isolation test failed first against
the unchanged three repository consumers. Core/test/API/Worker compilation
passed in 39s. The Asset query, catalog, evidence-scope, and full Modulith test
slice passed in 37s. The real PostgreSQL external-principal scope proof and the
Spring API admin-resource integration proof passed together in 1m35s, confirming
the internal transactional bean and preserving authorization/resource behavior.
The combined full Core/API/Worker gate passed in 7m04s. The documentation
operating-model check passed across 478 Markdown files and 8 mirrored domain
pairs; all 40 release-policy tests passed under Node 24.15; and the terminating
repository-wide `clean test` passed in 1m03s across 99 tasks. Mechanical
repository-import, zero-byte, migration-scope, diff-hygiene, and 18-path scope
checks passed.

PR #252 merged as `00aabe15` after Backend Java 25, documentation, evaluation,
secret, impact, release-preview, release-policy, and aggregate CI checks passed;
unaffected jobs skipped by surface detection. CodeRabbit approved the code and
raised one request for a duplicate ADR. Repository evidence showed the active
design plus challenge brief/verdict already contain the required alternatives,
decision, counterargument, and condition; the response cited those sources and
the resolved thread records why no duplicate decision was added. Both the PR
head and merge commit are ancestors of current `origin/main`.

## Thirty-eighth Pull Request Evidence

- Organization owns `KnowledgeAccessSubjectQuery` and the immutable
  `KnowledgeAccessSubject` value for current active department and Executive
  facts; `OrganizationResourceQuery` owns organization/department existence.
- Both JPA implementations remain package-private. Retrieval imports no
  `AppUser`, Organization repository, Department repository, AppUser repository,
  or `UserRole` type.
- Evidence scope and source visibility reload the persisted subject instead of
  trusting `CurrentActor` department or role claims. ADMIN remains non-Executive;
  inactive and foreign-tenant subjects fail closed.
- `AuthorizationResourceDirectory`, `KnowledgeEvidenceScopeResolver`, and
  `SecureSourceVisibilityAdapter` are the exact Retrieval consumers of the
  Organization owner queries.
- This code PR remains below 100 changed paths. Retrieval stays open for Source
  Ledger citation, Graph verifier, and adapter seams.

Local verification so far: the Organization isolation test failed first against
the unchanged repository/entity/role consumers. Core/test/API/Worker compilation
passed in 41s. The new owner-query, evidence-scope, source-visibility, and full
Modulith slice passed in 36s. PostgreSQL external-principal, API admin-resource,
and canonical Knowledge retrieval integration proofs passed together in 1m31s.
The first full Worker run exposed that its deliberately narrow component scan
did not include the new Organization owner adapters; adding the Organization
package to Worker wiring made all 40 Worker tasks pass in 3m42s, and the combined
Core/API/Worker gate then passed. The documentation operating-model check passed
for 478 Markdown files and 8 mirrored domain pairs. Release policy passed all 40
tests on Node 24.15.0. The mechanical audit found 23 changed paths, no migration,
no empty changed file, no forbidden Retrieval import, and a clean whitespace
diff. The terminating `clean test` passed in 1m26s with 99 actionable tasks.

PR #254 merged as `7cef296c` after Backend Java 25, documentation, evaluation,
secret, impact, release-preview, release-policy, and aggregate CI checks passed;
unaffected jobs skipped by surface detection. CodeRabbit was rate limited;
direct audit found no defect, review, inline comment, or review thread. Both the
PR head `b5428d33` and merge commit are ancestors of current `origin/main`.

## Thirty-ninth Pull Request Evidence

- Source Ledger owns one typed citation-evidence query that resolves a
  tenant-scoped ready revision, matching Knowledge Asset, and validated evidence
  blob into immutable metadata for Retrieval.
- The query keeps revision and blob repositories, entities, and status enums
  inside Source Ledger. Retrieval consumes only the evidence query/result/value
  and the existing storage contract.
- Missing/non-ready/mismatched revisions retain
  `CITATION_REVISION_NOT_CURRENT`; missing/unvalidated blobs retain
  `CITATION_BLOB_NOT_AVAILABLE`. Both remain opaque citation `404` responses and
  retain their distinct permission-audit reason.
- Object bytes are still opened only after current authorization succeeds, and
  blob length/hash are still checked against object-storage metadata before any
  `ALLOW` audit or response.
- This code PR remains below 100 changed paths. Retrieval stays open for the
  Graph verifier and remaining adapter seams.

Local verification started with both structural tests failing against the
current Source Revision/Evidence Blob entity, repository, and status imports in
48s. The owner query, typed unavailable results, Citation service, and structural
slice then passed in 41s; the complete citation plus Modulith slice passed with
API citation tests in 2m21s. A parallel full run lost its Gradle daemon with no
test failure while the machine had about 1.7 GB free RAM; after removing only
that worktree's orphan test worker, full Core, API, and Worker reruns passed
sequentially in 2m03s, 5m15s, and 4m07s. The documentation operating-model
check passed for 479 Markdown files and 8 mirrored domain pairs. Release policy
passed all 41 tests on Node 24.15.0. The mechanical audit found 15 changed paths,
no migration, no forbidden Retrieval import, and a clean whitespace diff. The
final terminating sequential `clean test`, rerun after the exhaustive sealed-result
switch refinement, passed in 9m05s with 99 actionable tasks.

After merging current `origin/main` at `39281c33`, the Citation plus full
Modulith slice passed again in 55s. The documentation check passed across the
new base's 485 Markdown files and 8 mirrored domain pairs, and all 41
release-policy tests passed again on Node 24.15.0.

PR #258 merged as `6ed738c2` after Backend Java 25, documentation, evaluation,
secret, impact, release-preview, release-policy, and aggregate CI checks passed;
unaffected jobs skipped by surface detection. CodeRabbit was rate limited;
direct audit against the architecture verdict found no defect, review, inline
comment, or review thread. Both the PR head `815640cd` and merge commit are
ancestors of current `origin/main`.

## Fortieth Pull Request Evidence

- Retrieval owns one `GraphEvidenceVerifier` contract and immutable
  `VerifiedGraphEvidenceScope`; its package-private implementation alone may use
  `KnowledgeEvidenceScopeResolver`, `ResolvedKnowledgeEvidenceScope`,
  `SecureKnowledgeRetrievalStore`, its retrieval scope, or
  `SecureRetrievalCandidate`.
- Graph exploration and export use verified per-Space evidence snapshots and
  retain their existing before/after authorization comparison and retry/fail
  behavior. Curation uses the verifier for governing chunk freshness and retains
  its stricter authorized-asset plus ACL-generation comparison.
- Graph imports no Retrieval resolver, resolved scope, store, store scope, or
  candidate. Its remaining Retrieval dependencies are the verifier/snapshot,
  existing retrieval-unavailable exception, and embedding profile contracts.
- Current authorization remains resolved before Graph reads; governing evidence
  must still match organization, Asset, revision, ACL snapshot, and chunk after a
  canonical store recheck.
- This code PR remains below 100 changed paths. Retrieval stays open for the
  remaining API/Worker adapter interfaces and final closure.

Local verification starts by changing the exact Graph-to-Retrieval dependency
test to the intended verifier-only surface and observing it fail against the
current resolver/store/candidate imports. The verifier, all three Graph use
cases, and both exact Modulith guards then passed their focused slice. The first
full Core run exposed the second temporary-open-boundary allowlist that still
named the retired Graph dependencies; after aligning that guard, the two
structural tests and full Core rerun passed. That first run also exhausted native
JVM memory while two unrelated worktrees were running Gradle concurrently; the
isolated sequential rerun passed in 2m10s. Full API and Worker reruns passed in
5m and 2m36s, including deployable Spring wiring. The documentation
operating-model check passed for 486 Markdown files and 8 mirrored domain pairs.
Release policy passed all 41 tests on Node 24.15.0. The mechanical audit found
20 changed paths, no migration, no empty changed file, no forbidden Graph import
of Retrieval implementation types, and a clean whitespace diff. The terminating
sequential `clean test` passed with 99 actionable tasks in 2m05s; after the
verifier test was strengthened to cover organization and chunk mismatches, its
focused rerun stayed green and a fresh terminating `clean test` passed all 99
tasks again in 1m41s.

After merging current `origin/main` at `f2cf3c67`, the four Graph/verifier test
classes plus the full Modulith verification slice passed in 1m35s. The
documentation operating-model check passed on the merged base for 501 Markdown
files and 8 mirrored domain pairs, and all 41 release-policy tests passed again
on Node 24.15.0.

PR CI's first product-release job passed its contract tests but rejected the
missing release disposition in the PR event payload. The PR now explicitly
skips an intermediate release because the project owner requested one release
only after the full refactor goal; a new synchronize event is required because
rerunning the original workflow retains its original PR payload.

CodeRabbit then found a valid fail-closed gap: an absent Space could degrade to
an empty asset set and generation zero, allowing export comparison or
deactivation guards to treat two absent scopes as stable. The fix makes snapshot
accessors reject unknown Spaces, explicitly denies export/deactivation before
read or write, and narrows canonical evidence rechecks to the requested Space's
assets. Candidate identity tests now vary organization, chunk, Asset, revision,
and current ACL independently; curation tests cover stale evidence and absent
Space deactivation. The duplicated Graph-side unavailable-scope translation and
Space comparison rules were consolidated to avoid authorization drift. The new
tests failed first against the permissive/default APIs; the corrected Graph,
verifier, and full Modulith slice passed in 35s, followed by full Core in 1m43s.
The terminating post-review `clean test` then passed all 99 tasks in 6m31s,
including uncached API and Worker tests affected by the Core boundary change.

After a second main sync at `8bf800c6` brought the governed document-action
Retrieval changes, the Graph/verifier classes plus full Modulith slice passed
again in 51s. Documentation passed for 506 Markdown files and 8 mirrored domain
pairs, and all 41 release-policy tests passed on Node 24.15.0.

PR #263 merged as `7772104d9733b6cb8361693cce42b3521f8a37f1` after all
required CI checks passed. CodeRabbit's fail-closed findings were fixed at head
`0a0f0eaaaf9a512762bccf10009ca66332f4e10d`; all five inline threads were
answered and resolved. Both the reviewed head and merge commit are ancestors of
current `origin/main`.

## Forty-first Pull Request Evidence

- Canonical hybrid search, GraphRAG search, citation/source content,
  authorization-resource lookup, bounded Asset inspection, and embedding
  profile resolution are adapter-facing interfaces rather than concrete types.
- Full evidence-scope resolution remains package-private and its internal scope
  value does not leak through the API inspection contract.
- Their default/JDBC implementations are distinct package-private classes, so
  API and Worker cannot import them. Existing method shapes and domain values
  remain unchanged.
- The API selects canonical or GraphRAG through those interfaces. The production
  Worker excludes the explicit canonical-query configuration, while Worker
  integration tests opt into that same configuration when exercising real
  search behavior.
- A failing-first structural test proves the seven adapter contracts are interfaces
  and their seven implementation types are non-public.
- Focused engine, content, scope, registry, API configuration/controller, and
  Worker integration tests pass; full Core/API/Worker and terminating repository
  gates follow before the PR is opened.
- This is a code PR below 100 changed files. Retrieval remains open only for
  root implementation/persistence internalization and its exact final closure.

Local verification started with the seven-contract interface guard failing on
the unchanged concrete classes in 28s. Core/API/Worker main and test compilation
then passed in 27s. Focused engine, content, scope, registry, Modulith, API
controller/configuration, API context, external-principal, admin-inspector, and
Worker PostgreSQL integration slices passed. The combined full Core/API/Worker
run completed 142 test classes with zero failures in about 5m43s. The docs
operating-model check passed across 506 Markdown files and 8 mirrored domain
pairs; all 41 release-policy tests passed on Node 24.15.0; and the terminating
repository-wide `clean test` initially passed 99 tasks in 1m09s. Exact API and
Worker ArchUnit dependency-surface guards were then added and passed in 57s; a
fresh terminating `clean test` including those guards passed 108 tasks in
5m18s. The mechanical audit found 41 changed paths, no migration, no empty file,
no external import of Retrieval implementation/scope/store/candidate types, and
a clean whitespace diff.

After merging current `origin/main` at `0b5b0cfd`, the full Modulith verifier,
both exact deployable dependency guards, and API engine-selection tests passed
again in 48s. The documentation check still passed across 506 Markdown files
and 8 mirrored domain pairs, and all 41 release-policy tests passed again on
Node 24.15.0.

CodeRabbit raised six inline findings. Five valid findings are fixed: evidence-
scope unavailability retains its cause, citation integrity mismatch records a
deny audit, duplicate canonical chunk rows collapse deterministically, all six
GraphRAG telemetry emitters share one fail-safe guard, and the bounded Asset
inspector independently rechecks relationship authorization plus model identity
before canonical SQL. Four characterization tests failed first on the unchanged
implementation and passed after the fixes. The focused Retrieval tests, full
Modulith verifier, API admin integration, and exact API/Worker dependency guards
then passed sequentially. The remaining UPSERT suggestion is rejected because
the repository uses PostgreSQL's default Read Committed isolation: the
`ON CONFLICT DO NOTHING` command may observe a concurrent uniqueness conflict,
and the following repository `SELECT` starts a new command snapshot that sees
the committed row; a no-op update would add writes and lock/trigger semantics
without closing a real visibility gap.

After the review-fix commits, the terminating sequential repository-wide
`clean test` passed all 99 tasks in 4m44s. The documentation operating-model
check passed for 506 Markdown files and 8 mirrored domain pairs, and all 41
release-policy tests passed again on exact Node 24.15.0. The PR diff remains 43
changed paths.

PR #266 merged as `fa226b0d676116292c662e204d30cf0ce326a1bc`
after all required CI checks passed. Five valid CodeRabbit findings were fixed,
the PostgreSQL UPSERT false positive was rejected with current Read Committed
documentation, and all six inline threads were answered and resolved. Both the
reviewed head `45518b1b940cbcb90e8b4611d06e4c456d75225a` and merge commit are
ancestors of current `origin/main`.

## Current Pull Request Gates

- Retrieval is a closed nested module with an exact allowlist for AI,
  authorization, its owner queries/named interfaces, Organization, Permission,
  and shared contracts.
- The public root API is pinned exactly. The embedding entity/repository,
  catalog implementation, evidence-scope exception and value, canonical store
  and retrieval scope, and secure candidate are package-private.
- Existing adapter contracts and their default/JDBC implementations retain the
  visibility established in PR #266; no endpoint, query, persistence, ranking,
  or authorization behavior changes.
- Failing-first structural tests prove both the previously open module and the
  leaked public root types. `modules.verify()` enforces the closed boundary.
- Focused registry, catalog, scope, hybrid, GraphRAG, content, verifier,
  PostgreSQL external-principal, API admin/configuration/boundary, and Worker
  boundary tests pass.
- This code PR remains below 100 changed files and completes Retrieval closure
  before the next Knowledge/Asset Registry slice begins.

Local verification started with the closed-module and exact-public-root tests
failing against the unchanged `Type.OPEN` module and seven leaked persistence/
runtime types. After internalization, both tests and `modules.verify()` passed
in 26s. The complete focused Core/API/Worker slice then passed in 4m06s,
including Spring Data/Hibernate wiring for the package-private embedding entity
and repository. The documentation operating-model check passed for 506
Markdown files and 8 mirrored domain pairs, and all 41 release-policy tests
passed on exact Node 24.15.0. The terminating sequential repository-wide
`clean test` passed all 99 tasks in 2m10s. The pre-stage diff contains 14
changed paths.

## Asset Registry Kernel Sequence

Independent review rejected the initial split between an Asset kernel and an
authorization persistence module. Role, outbox, lease completion, and readiness
belong with Asset in one transactional kernel; the authorization module is the
external OpenFGA projection edge only.

- [x] PR 1: move the six cross-module Asset vocabulary/error types to the exact
  parent-owned `assetregistry::api` named interface; keep Kernel absent and stay
  at or below 70 changed paths.
- [x] PR 2: introduce and immediately close Kernel; move the canonical
  Asset/role/outbox ledger, narrow the identity repository, extract the parent
  catalog read model, add parent draft locking, and implement parent-facing
  `assetregistry::api` command/query contracts; stay at or below 60 changed
  paths.
- [x] PR 3: move projection and convergence entry points, enforce transaction
  propagation `NEVER` around OpenFGA calls, close
  `assetregistry.authorization`, and update Worker wiring; stay at or below 20
  changed paths.
- [x] Run the focused Core/API/Worker gates, docs and release-policy checks,
  static analysis, and a terminating clean repository test for every slice.
- [x] Merge each code-bearing PR through CI and CodeRabbit before starting the
  next branch. Release only after all remaining Asset Registry slices and the
  full increment are complete.

PR 1 characterization first proved that a closed Kernel containing public
vocabulary was insufficient: the two focused tests passed while the full
Modulith verifier rejected Assistant's direct nested-module reference. The
independent reviewer amended the placement to the exact parent-owned
`assetregistry::api` named interface; the corrected named-interface test and
`modules.verify()` then passed in 10s. Full Core and API suites completed 621
tests with zero failures. Documentation checks and 23 release-policy tests
passed on exact Node 24.15.0, and the terminating repository-wide `clean test`
passed 99 tasks in 2m07s. JetBrains semantic inspection was unavailable, so the
documented Gradle/mechanical fallback was used. The complete PR remains at 68
changed paths.

PR 2's executable Modulith verifier exposed a cycle in the planned intermediate
state: parent projection code importing Kernel while Kernel consumed the parent
`assetregistry::api` interface produced `assetregistry -> kernel ->
assetregistry`. The already-selected PR 3 topology was therefore delivered in
the same code commit rather than weakening verification. Closed Kernel now owns
the canonical Asset, role, outbox/lease, and readiness ledger; closed
Authorization owns only external projection/convergence; the parent invokes
projection through `assetregistry::api`. Registration, role, and portfolio
commands join the parent transaction with `MANDATORY`, queue operations own
short `REQUIRES_NEW` transactions, OpenFGA calls reject ambient transactions
with `NEVER`, and the three mutable Skill/review flows serialize on the Draft
row. Code commit `573c1d1f` contains 41 changed paths. Full Core passed 453
tests, the Asset Registry API integration suite passed 22, and Worker passed 65,
all with zero failures. Documentation hygiene passed for 509 Markdown files and
8 mirrored domain pairs; all 23 release-policy tests passed on exact Node
24.15.0. JetBrains semantic inspection was unavailable, so the documented
Gradle, `modules.verify()`, import-boundary, and `git diff --check` fallback was
used. The first clean run reached 94 tasks before an orphaned Gradle daemon
caused native-memory exhaustion; after removing that process, the terminating
single-worker `clean test` passed all 99 tasks in 4m18s with a 2 GB daemon heap.

CodeRabbit's full review of PR #270 produced ten inline findings and one
outside-diff finding. Commit `01e26c23` accepted eight valid improvements:
provider-failure logging without tuple disclosure, required-field validation
for the public authorization target, the unclaimed projection branch, runtime
Spring-proxy enforcement of both `NEVER` boundaries including the package-
private batch overload, both completion transaction boundaries, non-Skill
coordinate rejection, class-literal API pinning, and the intentional exact-
package ArchUnit comment. The Draft lock-timeout suggestion was rejected
because the repository has no established finite value and its PostgreSQL
baseline explicitly leaves `lock_timeout` at zero. The terminal portfolio
finding was rejected as unreachable after the existing withdrawn-release
guard, and the optional role partial index remains outside this no-schema
refactor because the locked canonical Asset already serializes all Kernel role
writes. All review threads were answered and resolved. Full Core passed 456
tests with zero failures; the terminating sequential repository-wide
`clean test` then passed all 99 tasks in 8m52s on the reviewed code head.

PR #270 merged as `9b88c33356515612b4ccb9e51e7e66769ddc2dc9`
after all required checks passed and every CodeRabbit thread was resolved. The
Prompt profile is the next code-bearing Asset Registry slice; release remains
deferred until every profile module and the full increment are complete.

## Asset Registry Prompt Sequence

- [x] Add failing-first tests for the exact `profile` and `consumption` named
  interfaces, the closed Prompt allowlist and public surface, forbidden parent
  dependencies, Assistant isolation, and the built-in profile set.
- [x] Move the profile SPI and immutable release-use vocabulary to their exact
  parent-owned named interfaces; make `AssetRegistryService` implement the
  narrow query without changing the default parent API.
- [x] Move the fourteen Prompt production types and focused tests, close the
  module immediately, and internalize renderer, schema, profile implementation,
  coordinator, entities, repositories, and status.
- [x] Add the parent-owned Prompt operations/results contract and delegate Assistant's
  existing metadata-only preparation flow without changing endpoint or wire
  behavior.
- [x] Pass focused Core/API/Worker, docs, release-policy, static-analysis, and
  terminating clean repository gates; keep the code-bearing PR at or below 69
  file entries, then merge through CI and CodeRabbit.

Fable 5 returned blank zero-token responses twice at 98% weekly usage. The
independent Orca fallback, counterattack, and factual amendment accepted the
closed boundary with the corrections recorded in
[the verdict](assetregistry-prompt-challenge-verdict.md). No release occurs at
this intermediate checkpoint.

The first implementation exposed two additional topology facts through
`ApplicationModules.verify()`: Core Assistant cannot import a nested Prompt
module directly, and Prompt's existing SPI, errors, entities, and digests
require `assetregistry::api`, `shared::error`, and `shared`. The same
independent Orca review session selected a parent-owned
`assetregistry::prompt` operations interface implemented by a package-private
Prompt adapter. The corrected nine-entry allowlist and parent contract then
made the full 67-test structure gate pass.

The first parent contract used interface projections. Although Modulith stayed
green, the live OpenAPI contract rejected the empty generated `FormVariable`
schema. The same reviewer selected the concrete-contract correction:
preparation, render, and run result records moved to `assetregistry::prompt`;
the adapter returns them unchanged. Both the focused structure gate and
`OpenApiContractTests` then passed without modifying `contracts/openapi.json`.

Prompt structure and behavior tests passed with the exact parent named
interfaces, nine-entry closed-module allowlist, three-type nested public surface,
Assistant isolation, metadata-only preparation, deterministic render/run, and
built-in profile set. The full clean suites passed 474 Core, 182 API, and 65
Worker tests with zero failures. The live OpenAPI model remained structurally
identical without a contract rewrite; the Worker context also proved Prompt
execution stays conditional on the API-side AI/search capabilities.

CodeRabbit's completed review produced three findings. Preparation service
visibility, exhaustive variable-type mapping, and recursive immutability of
nested JSON contract values were all corrected with focused tests; the parent
Prompt interface and OpenAPI shape remain unchanged.

Documentation hygiene passed for 516 Markdown files and 8 mirrored domain
pairs. Release policy passed 18 Tegami/product tests and 23 workflow/policy
tests on exact Node 24.15.0. JetBrains semantic inspection remained
unavailable, so the documented Gradle compilation, executable Modulith,
package/import search, exact-public-surface, `git diff --check`, and clean-test
fallback was used. The terminating repository-wide `clean test` passed all 99
tasks in 7m05s. Rename-aware diff accounting is exactly 69 changed paths, and
the Asset Registry root package decreased from 90 to 72 Java files.

PR #274 merged as `6b36e1282dab70e4b224c17d4069e8749ad3edb7`
after every required CI check passed. CodeRabbit's completed full review found
three valid issues; all were fixed and all three threads were resolved. The
reviewed head `3fe3183e` passed the terminating 99-task clean repository test
in 8m01s. Release remains deferred. The Skill family is the next code-bearing
slice and starts with an independent boundary challenge.

## Asset Registry Skill Sequence

- [x] Challenge the exact Skill ownership, parent contract, supersession, and
  delivery boundary. Fable 5 returned blank zero-token responses twice; the
  independent Orca fallback and counterattack selected one Skill semantics
  module but rejected both the catch-all parent interface and split cleanup
  ownership. The binding result is recorded in
  [the verdict](assetregistry-skill-challenge-verdict.md).
- [x] PR 1: add failing-first exact-interface and importer guards, then
  establish parent-owned `skill-package`, `skill-delivery`, `skill-cleanup`,
  and `skill-storage` capabilities below 60 changed paths.
- [x] PR 1: route current parent-package Skill flows through those capabilities
  without a nested module, storage-key exposure, transaction change, schema
  change, or wire-contract change.
- [x] PR 1: pass focused Core/API/OpenAPI/Worker/connector/MinIO/integration
  gates, docs and release policy, static analysis fallback, and a terminating
  clean repository test.
- [x] PR 1: merge through CI and CodeRabbit without releasing.
- [x] PR 2: add failing-first closed-module, exact-public-surface,
  forbidden-parent-import, and external-consumer guards, then move and
  immediately close `assetregistry.skill` below 70 changed paths.
- [ ] PR 2: pass all focused and terminating gates, merge through CI and
  CodeRabbit, then continue to the next Asset Registry profile family without
  releasing early.

The failing-first named-interface characterization produced the expected
`NoSuchElementException` before any capability package existed. Commit
`4a158882` then established the four exact parent interfaces and importer
guards, moved storage/compensation/reference lookup/storage opening behind
parent implementations, retained the complete supersession aggregate in the
parent, and reduced Worker cleanup visibility to an immutable summary. The
Skill semantics layer receives no object key and imports neither storage nor
cleanup capability.

The implementation changes 43 rename-aware paths, below both the reviewed
60-path target and the hard 100-file PR cap. Full suites passed 485 Core, 186
API, 67 Worker, 120 connector, and 6 MinIO tests with zero failure, error, or
skip in 7m49s. OpenAPI and PostgreSQL Asset Registry integration tests passed
without a contract or schema rewrite. The documentation operating-model check
passed for 531 Markdown files and 8 mirrored domain pairs. Release policy
passed 18 Tegami/product and 23 workflow/policy tests on exact Node 24.15.0.
JetBrains inspection remained unavailable, so Gradle compilation, executable
Modulith and exact-consumer tests, zero-byte/package/migration checks, and
`git diff --check` supplied the documented fallback. The terminating
repository-wide `clean test` passed all 108 tasks in 7m15s. Release remains
deferred.

PR #282's first CodeRabbit pass completed after every required CI job was
green and raised three inline findings plus two outside-diff test/exception
suggestions. Commit `518e0277` implements the valid subset: payload/artifact
consistency now fails before storage I/O for both import and replacement,
Jackson 3 serialization failure is translated to the stable staging-unavailable
contract, and six focused tests cover pre-storage rejection, missing/non-blob
references, and stream closure when manifest construction fails. The full Core
suite then passed 491 tests with zero failures. Restricting the parent artifact
value to only `application/zip` was rejected because readable legacy Skill
schema intentionally permits `application/octet-stream`; duplicating coordinate
validation in the lifecycle service was rejected because Kernel already
normalizes and validates both coordinates before persistence, with a stricter
Skill slug grammar than delivery resolution. API exception translation was
also confirmed to serialize only the stable top-level business message, never
the storage cause. The PR remains below the 100-file cap and release remains
deferred.

PR #282 merged as `d3509d6d` after all required checks and CodeRabbit threads
were resolved. PR 2 then began with the expected failing closed-module probe
and commit `4c7a8bf2` closed `assetregistry.skill` in 42 rename-aware paths.
The child now exposes exactly three operation interfaces, one GitHub source
port, and three immutable results; all implementations and semantics remain
package-private. It consumes only the parent package and delivery capabilities,
never storage, cleanup, parent implementation, or an object key, while the
parent has no dependency on the child. API response mapping preserves the
existing Asset view and GitHub import wire shapes, and the nested inspection
entry retains the existing OpenAPI schema name. Focused Core, API, connector,
OpenAPI, Asset Registry integration, and Modulith gates are green. Worker and
MinIO consumer suites also passed, and the terminating repository-wide
`clean test` completed all 99 tasks with 1,265 tests and zero failure, error,
or skip. Documentation hygiene passed for 534 Markdown files and 8 mirrored
domain pairs; release policy passed 18 Tegami/product and 23 workflow/policy
tests on exact Node 24.15.0. The mechanical fallback found 47 total changed
paths, zero missing package declarations, zero changed zero-byte files, zero
forbidden Skill imports, and a clean diff. The PR review loop remains before
merge, and release remains deferred.

CodeRabbit's completed PR 2 review raised two inline findings and one
outside-diff authorization suggestion. Commit `5f7faa70` fixes the valid
failure-isolation gap: API resolution of each imported Asset is now independent,
so a projection/read failure preserves every sibling result and reports a safe
per-item code without changing the wire shape. It also adds the missing
replacement pre-authorization regression and removes only the redundant middle
preflight check from the already-authorized GitHub path. Removing the direct
import or replacement pre-authorization was rejected because those checks
prevent unauthorized ZIP reads; the parent command still rechecks authority
immediately before mutation. Eliminating the bounded maximum-20 full-view reads
would require a new parent projection capability outside the challenged
boundary, so that performance redesign remains separate from this correctness
fix. Focused tests went red before the fix, then 686 full Core/API tests passed
with zero failure, error, or skip. The follow-up repository-wide `clean test`
passed all 99 tasks and 1,267 tests; docs hygiene, release policy, and diff
checks also remained green. The PR now changes 48 paths, still below the cap.
