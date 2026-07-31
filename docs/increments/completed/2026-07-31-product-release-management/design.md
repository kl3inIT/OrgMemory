# Product Release Management Design

## Problem

OrgMemory currently publishes immutable application and documentation images
from green `main` commits, but it has no product-level version, curated
changelog, version pull request, tag, or GitHub Release. The repository is a
polyglot product: deployable Java services are built by Gradle while the web,
CLI, and docs applications are pnpm workspaces. Treating any one package
registry as the product release boundary would misrepresent that system.

## Goals

- Manage one version and changelog for the complete OrgMemory product.
- Use Tegami to collect release entries, create a Version Packages pull
  request, and create a tag and GitHub Release after green `main` CI.
- Keep the existing SHA-addressed image and deployment pipelines authoritative
  for executable artifacts.
- Keep normal pull requests reviewable by CodeRabbit while the repository
  remains public.
- Make release-note safety and release workflow behavior executable in CI.
- Bind every semantic release to the immutable product and docs artifact
  evidence that it describes.

## Non-Goals

- Publishing OrgMemory to npm, Maven Central, or another package registry.
- Giving every Gradle or pnpm module an independently released version.
- replacing immutable `sha-<commit>` image identifiers with mutable semantic
  version tags.
- Publishing credentials, secret values, customer data, private keys, or
  unredacted security incident details in release entries.

## Proposed Release Unit

Add one synthetic Tegami workspace package backed by
`release/product.json`. Its package manager is the repository-owned `product`
provider, its package name is `orgmemory`, and its version is the canonical
product version. Tegami writes the curated product changelog to
`release/CHANGELOG.md`.

A small repository plugin will:

1. resolve that synthetic product package;
2. apply the selected semantic bump to `release/product.json`;
3. report the product as publishable without uploading it to a registry;
4. validate the checked-in consolidated artifact manifest before reporting a
   registry-free publication;
5. set the immutable release tag to `v<version>`; and
6. let Tegami's GitHub plugin own the Version Packages pull request, tag, and
   GitHub Release.

All discovered npm workspaces are ignored by Tegami. They keep their existing
package-local metadata and are not separate OrgMemory release units.

## Delivery And Trust Boundary

The release workflow runs only after the repository's `CI` workflow completes
successfully for a same-repository push to `main`. It checks out the exact
green commit. A normal merge containing pending `.tegami/*.md` entries creates
or updates the Version Packages pull request. Merging that pull request causes
the next green-main run to consume the publish lock and create the tag and
GitHub Release.

The GitHub Release is metadata over an already verified commit. Existing image
workflows continue to publish immutable SHA-addressed images and release
manifests. Changes limited to `release/**` and `.tegami/**` must not rebuild or
deploy product images.

Before creating a Version Packages pull request, the release workflow resolves
the most recent complete production-image manifest and docs-image manifest
applicable to the green source commit. It validates all component references,
digests, and source SHAs, then commits a consolidated
`release/artifacts.json` in the Version PR. Unchanged components may retain
prior source SHAs and digests. The custom provider refuses to publish unless
that evidence is complete and still resolves immutably. The GitHub Release
includes the manifest as an attached asset and summarizes its source commits.

An existing `v<version>` tag is accepted only when its peeled remote target is
the exact intended release commit. A mismatch is a hard failure. Post-publish
verification re-reads the remote tag and GitHub Release target. The publish
lock remains until tag, Release, artifact attachment, and verification are all
complete, so partial failure is retried rather than silently cleaned up.

Pull-request preview executes only untrusted branch code with read-only
permissions and uploads an artifact. A workflow with write permissions must
never execute a pull request's code through `pull_request_target`.

## Public Repository Boundary

The repository remains public so CodeRabbit can continue reviewing pull
requests under the owner's current setup. Every committed `.tegami/*.md` entry
is consequently public immediately, before a GitHub Release exists. Technical
release detail is allowed, but credentials, tokens, private keys, customer
data, managed-secret values, private infrastructure access details, and
unredacted sensitive incident or vulnerability detail are forbidden.

CI adds a pinned dedicated secret scanner and structural release-entry checks.
Because a public pull request is already disclosure, CI is a detection and
merge gate, not a prevention guarantee. Human review and local scanning are the
pre-publication controls. Agent guidance requires a public-facing entry for
every user-visible change and permits `skip-release` only for changes that
genuinely have no product or operator impact.

The provider imports only contracts exported by `tegami`; it never imports
`tegami/src/**`, `tegami/dist/**`, or calls `_internal`. The npm provider has
lockfile updates disabled, and contract tests require every ignored npm
manifest, Gradle file, deployment file, and pnpm lockfile to remain byte
identical. A Version PR diff allowlist admits only the consumed `.tegami`
entries, publish lock, product version, product changelog, and consolidated
artifact manifest.

The writable release workflow uses non-cancelling concurrency and revalidates
that a successful same-repository push CI run targets `main`, names a valid
commit, and still equals the current `origin/main` tip immediately before any
branch, tag, or Release mutation. Stale runs exit without invoking writable
Tegami hooks.

## Strongest Counterargument

Tegami is designed primarily around package graphs and registries. Adding a
custom pseudo-package and publish provider increases maintenance and makes
OrgMemory responsible for compatibility with Tegami plugin hooks. A simpler
GitHub Actions script could edit a version file and create a GitHub Release
without introducing this abstraction.

## Repository Evidence And Final Choice

- the root package is private and fixed at `0.0.0`, while Gradle owns the Java
  services;
- root `package.json` is in web, CLI, docs, and image path filters, so using it
  as product version state would trigger unrelated verification and builds;
- product delivery is already based on green main SHAs and immutable image
  manifests;
- Tegami 1.2.7 exposes workspace resolution, draft application, publish
  preflight, publish, and publish-plan hooks needed by a registry-free release
  unit;
- Tegami's GitHub plugin already owns version pull requests, tags, and releases.

Choose the synthetic product package while pinning Tegami and testing every
custom hook. This preserves one release unit and reuses Tegami's state machine
without pretending OrgMemory is an npm package.

The rejected alternative is making the root npm package the product release
unit. It would couple product versioning to JavaScript workspace behavior and
cause version-only pull requests to fan out into unrelated builds.

The accepted counterargument changes the publication definition: the custom
provider is not allowed to equate a no-op registry call with a release. It may
report success only after validating the consolidated immutable artifact
manifest. A handwritten GitHub-only releaser remains rejected because it
would duplicate Tegami's Version PR, lock, changelog, retry, tag, and Release
state machine while retaining the same artifact-evidence problem.

## Upstream Reference Baseline

| Source | Reviewed revision | Reused boundary |
| --- | --- | --- |
| `fuma-nama/tegami` | `86f2315e1adef4606ac4ef51333be53346520658` | CI phases, GitHub Version PR/release lifecycle, plugin hooks, publish lock |
| `fuma-nama/fumadocs` | `81c88c6bb3b03750ff707e6a9470b7ab20dfec7b` | production `createCli` entrypoint and GitHub plugin configuration |
| OrgMemory | `d8e2693f9b185e5612786f420e0694fff2da825d` | current CI, immutable images, polyglot workspace, and path filters |
