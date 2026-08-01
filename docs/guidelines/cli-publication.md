# OrgMemory CLI Publication

`@orgmemory/cli` is a public transport binary for authenticated OrgMemory
operations. Public package visibility does not make the OrgMemory product,
Skill registry, or governed Skill content open source.

The published package carries the package-local proprietary `LICENSE`. It
permits authorized customers and evaluators to execute an unmodified CLI
against services they may access; it does not grant redistribution,
modification, sublicensing, or derivative-work rights.

## Release boundary

The CLI has an independent SemVer version in `apps/cli/package.json`. It is not
derived from `release/product.json` and is not published by the whole-product
release workflow. Every consumer command pins both the CLI version and the
authorized Skill release version.

`.github/workflows/publish-cli.yml` is the only steady-state publisher. It is a
manual workflow protected by the `npm-production` GitHub environment. The run
accepts an exact current green `main` SHA and exact package version, uses Node
24 and npm Trusted Publishing OIDC, emits npm provenance, inspects the tarball,
and verifies the registry result and executable before succeeding. It does not
use a long-lived npm token or dependency cache.

## First-publication bootstrap

The `@orgmemory/cli` package record now exists. Do not repeat its one-time
bootstrap or perform later releases from a workstation. The observed first
publication required this controlled exception because the live registry
returned `404 Package not found` when trust was configured before any package
record existed:

1. Verify that the project owner controls the npm `orgmemory` organization,
   has `auth-and-writes` 2FA enabled, and that the expected maintainers are the
   only owners. An `E404` response does not prove namespace ownership.
2. Publish the inspected, executable `0.0.0` tarball once under the `bootstrap`
   tag with interactive 2FA and without local provenance. This version exists
   only to create the npm package record and is never a consumer handoff.
3. Configure the protected GitHub `npm-production` environment and establish
   publish trust from the owner-authenticated workstation with npm 12:

   ```text
   npm exec --yes --package=npm@12.0.2 -- npm trust github @orgmemory/cli \
     --file publish-cli.yml --repo kl3inIT/OrgMemory --env npm-production \
     --allow-publish --yes
   ```

4. From the exact reviewed, green `main` commit, dispatch
   `.github/workflows/publish-cli.yml` with its matching package version and
   explicit `PUBLISH` confirmation. The workflow uses Node 24, builds and tests
   the CLI, packs the real tarball, executes its `orgmemory` binary locally,
   then publishes through npm Trusted Publishing with provenance.
5. Verify `npm view @orgmemory/cli@<version> --json`, registry integrity and
   provenance, then execute the exact registry version through `npm exec`.
6. Only then may the product or public docs render a pinned `npx` command for
   that verified version.

Version `0.1.0` was published by the protected OIDC workflow with registry
integrity and SLSA provenance and is the first consumer release. The workflow
is retry-safe: an immutable version may be reused only when its registry
integrity equals the reviewed tarball, and verification keeps polling until the
attestation is visible. Later versions run only through that workflow. If any
registry, provenance, version, commit, ownership, or integrity proof is
missing, do not publish and do not activate the command in the UI.
