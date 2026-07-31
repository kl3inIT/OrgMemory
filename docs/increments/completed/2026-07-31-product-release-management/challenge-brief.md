# Independent Architecture Challenge Brief

## Requested Verdict

Adversarially review the proposed production release-management architecture.
Return exactly one recommendation: `ACCEPT`, `ACCEPT WITH MUST-FIXES`, or
`REJECT`. Enumerate must-fixes, cite repository/upstream evidence, and identify
any unsafe assumption. Do not edit the repository.

## Proposal Under Challenge

OrgMemory will use Tegami 1.2.7 for one synthetic whole-product package rather
than npm or Gradle packages. `release/product.json` stores its version and
`release/CHANGELOG.md` stores its changelog. A custom product provider resolves
the package, writes its bump, reports a registry-free publish, and preselects a
`v<version>` tag. Tegami's GitHub plugin owns the Version Packages pull request,
publish lock, tag, and GitHub Release.

The repository remains public for the owner's current CodeRabbit review flow.
`.tegami/*.md` may contain ordinary technical release detail but no secrets,
credentials, customer data, private access details, or unredacted sensitive
security detail.

A dedicated workflow runs only following successful same-repository `CI` push
runs on `main`, at the exact green SHA. Pull-request preview runs with read-only
permissions. Existing SHA-addressed image and deployment workflows remain the
only executable artifact pipeline; release-only changes must be image no-ops.

## Operational Motivation

The product needs curated, reviewable changelogs and stable GitHub Releases
like other private or public software products, while the implementation is a
polyglot monorepo whose deployable unit is not an npm package. The solution
must be production-safe from the first release and must preserve current CI,
CodeRabbit review, immutable images, deployment manifests, and rollback.

## Repository Evidence To Inspect

- `package.json` and `pnpm-workspace.yaml`
- `.github/workflows/ci.yml`
- `.github/workflows/build-images.yml`
- `.github/workflows/build-docs.yml`
- `.github/workflows/deploy-production.yml`
- `.github/workflows/deploy-docs.yml`
- `ARCHITECTURE.md`
- `CLAUDE.md`
- `docs/increments/active/2026-07-31-product-release-management/design.md`

The reviewed OrgMemory base is
`d8e2693f9b185e5612786f420e0694fff2da825d`.

## Comparable Source Evidence

| Source | Local pinned checkout | Revision | Questions to verify |
| --- | --- | --- | --- |
| Tegami | `D:/OrgMemory/tmp/upstream-tegami-20260731` | `86f2315e1adef4606ac4ef51333be53346520658` | plugin ordering, graph identity, draft application, preflight, publish lock, Git tag/release semantics, CI threat model |
| Fumadocs | `D:/OrgMemory/tmp/upstream-fumadocs-20260731` | `81c88c6bb3b03750ff707e6a9470b7ab20dfec7b` | real Tegami CLI/config usage and differences from OrgMemory's release boundary |

## Required Attacks

1. Can the pseudo-package create a false successful publish or a tag without a
   deployable artifact?
2. Can workflow ordering publish from a stale, fork, failed, or non-main SHA?
3. Can a Version PR retrigger image builds, deployment, or infinite release
   loops?
4. Can the GitHub plugin or publish lock create a partial release that is hard
   to recover?
5. Does keeping `.tegami` public leak more than the accepted repository
   boundary, and are CI checks realistically enforceable?
6. Does the custom provider rely on unstable Tegami internals rather than
   exported plugin contracts?
7. Is a simpler non-Tegami implementation materially safer or easier to
   operate?
8. Which exact tests and workflow assertions are mandatory before merging?
