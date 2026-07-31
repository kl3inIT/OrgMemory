# Product Release Management Verification

## Local Evidence

Verified in `D:/OrgMemory-worktrees/tegami-release-management` on 2026-07-31.

- `pnpm release:check` — passed TypeScript strict checking, twelve provider and
  pinned-Tegami contract tests, eleven release/workflow policy tests, structural
  entry validation, writable-workflow trust assertions, and image/deploy no-op
  assertions.
- Node 24.18.1 direct test execution — passed the provider and release-policy
  suites on the repository's required Node major.
- `go run github.com/rhysd/actionlint/cmd/actionlint@v1.7.12` — passed all
  GitHub Actions workflows.
- `python scripts/check_docs.py` — passed the documentation operating model.
- Gitleaks 8.30.1 `dir` scan — passed the current worktree after three precise
  generated-test-key/parser-marker suppressions.
- Gitleaks 8.30.1 `git` scan — passed all 366 existing commits with the three
  matching historical false-positive fingerprints suppressed.
- `git diff --check` — passed.

The test suite proves:

- strict product and artifact schemas and full SemVer parsing;
- a single `product:orgmemory` bump from a pending entry;
- byte identity for ignored root/workspace npm manifests, pnpm lock state,
  Gradle files, and deployment files;
- a temporary Git repository and bare remote through Tegami draft, version,
  changelog, publish lock, and dry-run publish;
- tag collision and stale-main rejection;
- exact workflow decision-run and manifest-run provenance, downloaded manifest
  comparison, and live GHCR digest checks;
- rejection of forged manifest bytes and mismatched registry digests;
- content comparison for an existing GitHub Release `artifacts.json` asset;
- completed-lock recognition when its verified release tag is an ancestor of
  a newer green main commit;
- idempotent successful publication behavior;
- retry after injected Git tag-push and GitHub Release-creation failures using
  the production Tegami plugin composition;
- product-impacting PR entry/`skip-release` enforcement and Version PR
  exemption.

## Delivery Evidence

- PR #168 added the production Tegami workflow and merged as `cc3ebb3` after
  required CI and CodeRabbit review.
- PR #169 repaired generated version commit preflight and merged as `27fae54`.
- PR #172 allowed immutable image SHA references and merged as `7a84cbb`.
- PR #173 normalized the generated changelog layout and merged as `8499c10`.
- PR #170, **Version Packages**, merged as `fe3b303`.
- The release workflow completed successfully for `fe3b303`.
- Lightweight tag `v0.1.0` resolves exactly to `fe3b303`, the Version Packages
  merge commit.
- GitHub Release `v0.1.0` was published from `main` on 2026-07-31.
- The release contains the immutable `artifacts.json` manifest with a recorded
  SHA-256 digest.
- `release/product.json`, `release/CHANGELOG.md`, and the publish lock retain
  the durable product version, generated history, and publication evidence.
