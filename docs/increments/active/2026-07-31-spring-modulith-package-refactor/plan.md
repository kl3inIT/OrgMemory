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

## Current Pull Request Gates

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
