# Product Release Management Plan

Execute the accepted [design](design.md) only after the independent
architecture challenge is recorded in `challenge-verdict.md`.

## Architecture Gate

- [x] Inspect the current release, CI, image, deployment, and package boundaries.
- [x] Pin and inspect current Tegami and Fumadocs upstream revisions.
- [x] Record the exact proposal and strongest counterargument.
- [x] Obtain an independent architecture verdict and record every must-fix item.
- [x] Close every must-fix in the challenge verdict with executable evidence.

## Release Model

- [x] Add `release/product.json` as the canonical whole-product version.
- [x] Add and test the registry-free Tegami product provider.
- [x] Configure Tegami 1.2.7 with only the synthetic OrgMemory release unit.
- [x] Generate `release/CHANGELOG.md`, `v<version>` tags, and GitHub Releases.
- [x] Resolve and validate one complete immutable product/docs artifact
  manifest and attach it to every GitHub Release.
- [x] Reject a pre-existing version tag unless it peels to the intended commit.
- [x] Add a first public-safe pending release entry.

## Automation And Safety

- [x] Add release-surface detection and verification to the required CI gate.
- [x] Add read-only pull-request preview artifact generation.
- [x] Run Tegami only after same-repository green-main CI and check out its
  exact commit.
- [x] Verify through executable path-policy assertions that version-only
  changes do not build or deploy product images.
- [x] Add a pinned required secret scanner and state its public-repository
  detection boundary accurately.
- [x] Enforce an allowlisted Version PR diff and byte-identity of ignored npm,
  Gradle, deployment, and lock files.

## Agent And Operator Guidance

- [x] Add a concise release-entry workflow to the repository guideline without
  duplicating Tegami's generated agent block.
- [x] Document entry creation, bump selection, `skip-release`, Version PR,
  rollback, artifact carry-forward, and partial tag/Release recovery.
- [x] Record implemented release mechanics in `ARCHITECTURE.md`.

## Delivery Gate

- [x] Run focused release tests and repository documentation checks.
- [x] Run the complete affected CI surface locally where reproducible.
- [x] Open one pull request and complete CI and CodeRabbit review.
- [x] Merge and verify the green-main release workflow creates or updates the
  Version Packages pull request.
- [x] Rehearse the first version release, verify the exact tag and GitHub
  Release, and retain evidence.
- [x] Consolidate durable facts, move the increment to completed, and update
  the roadmap and Northstar checkpoint.
