# Product Release Management Architecture Challenge Verdict

## Review Identity

- Reviewed OrgMemory base: `d8e2693f9b185e5612786f420e0694fff2da825d`
- Tegami reference: `86f2315e1adef4606ac4ef51333be53346520658`
- Fumadocs reference: `81c88c6bb3b03750ff707e6a9470b7ab20dfec7b`
- Review scope: synthetic product package, custom provider, GitHub workflow,
  artifact evidence, public release entries, tag/retry semantics, and CI
- Reviewer: fresh independent read-only architecture challenge
- Verdict: **ACCEPT WITH MUST-FIXES**

## Must-Fix Findings

1. A registry-free `published` result is not artifact evidence. Every GitHub
   Release must include a validated immutable manifest for the complete product
   image set and docs image. Release-only commits carry forward the prior
   verified digests rather than rebuilding or deploying them. Missing or
   invalid evidence fails publication.
2. The writable `workflow_run` boundary must require successful same-repository
   `push` CI on `main`, a valid 40-character SHA, exact checkout, ancestry, and
   equality with the current `origin/main` tip immediately before mutation.
   Concurrency is non-cancelling and stale runs no-op.
3. An existing `v<version>` tag is valid only when its peeled target equals the
   intended release SHA. Verify the remote tag and GitHub Release target after
   publication and document every partial-failure recovery path.
4. The custom provider may use only Tegami's exported contracts. Tests must
   prove plugin ordering, publish status, and that the npm provider changes no
   ignored package manifest or lockfile. Configure `npm.updateLockFile: false`
   and enforce an allowlisted Version PR diff.
5. The repository has no established dedicated secret scanner. Add a real,
   pinned required scanner and correct the design: CI detects after content has
   reached a public PR; human and local review are still prevention controls.
6. Make the full ordinary-PR to Version-PR to one-release lifecycle,
   idempotent retries, failed-lock behavior, and image/deploy no-op properties
   executable tests.

## Unsafe Assumptions Rejected

- green CI alone proves a complete executable artifact set;
- `{ type: "published" }` proves registry-free publication;
- tag existence proves its target;
- Tegami `ignore` guarantees npm manifests and lockfiles remain untouched;
- Fumadocs' npm publication architecture validates OrgMemory's custom product
  provider;
- CI can prevent disclosure after a secret is pushed to a public pull request;
- a branch filter alone makes a writable `workflow_run` trustworthy.

## Mandatory Evidence Before Merge

- provider unit tests for parsing, bumps, atomic writes, exact tag identity,
  preflight/status, artifact validation, idempotency, and collision failure;
- a pinned Tegami 1.2.7 contract test in a temporary Git repository and bare
  remote;
- byte-identity assertions for npm/Gradle/deployment files and a strict Version
  PR diff allowlist;
- failure injection across provider, tag, tag push, GitHub Release, and artifact
  resolution;
- workflow fixtures for failed/stale/fork/non-main runs, wrong tags, missing
  evidence, and concurrency;
- `actionlint` plus a policy assertion forbidding writable execution of
  pull-request-controlled code;
- no-op assertions for images and both production/docs deployments;
- a disposable version/tag rehearsal verifying the remote tag SHA, GitHub
  Release target, attached artifact manifest, and retry behavior.

## Resolution

The design accepted every must-fix. A final independent closure review after
implementation returned **CLOSED** with no remaining pre-merge blocker. The
review confirmed exact decision/manifest workflow provenance, downloaded
artifact comparison, live GHCR digest validation, Release-asset byte
comparison, current-main checks at every writable boundary, pending-lock
protection, complete release-impact routing, and production-composition retry
tests. The real GitHub release rehearsal remains a post-merge delivery gate.

No repository visibility change is in scope; the repository remains public by
owner direction.
