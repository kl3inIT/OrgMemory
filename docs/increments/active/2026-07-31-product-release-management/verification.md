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

## Remaining Delivery Evidence

- GitHub CI and CodeRabbit review on the implementation pull request;
- green-main creation of the Version Packages pull request;
- CI and CodeRabbit review on that generated pull request;
- the first `v0.1.0` tag and GitHub Release;
- downloaded Release asset, remote tag SHA, artifact digests, no-op image/docs
  build jobs, no deployments, and idempotent retry evidence.

These require the implementation to merge and therefore remain open delivery
gates rather than local implementation claims.
