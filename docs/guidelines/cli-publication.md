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

Current npm 11 can establish the GitHub trust relationship before the package
exists. The first public version therefore uses the same OIDC-only workflow as
later versions; no interactive publish token is permitted:

1. Verify that the project owner controls the npm `orgmemory` organization,
   has `auth-and-writes` 2FA enabled, and that the expected maintainers are the
   only owners. An `E404` response does not prove namespace ownership.
2. Configure the protected GitHub `npm-production` environment and establish
   trust from the owner-authenticated workstation:

   ```text
   npm trust github @orgmemory/cli --file publish-cli.yml \
     --repo kl3inIT/OrgMemory --env npm-production --yes
   ```

3. From the exact reviewed, green `main` commit, dispatch
   `.github/workflows/publish-cli.yml` with its matching package version and
   explicit `PUBLISH` confirmation. The workflow uses Node 24, builds and tests
   the CLI, packs the real tarball, executes its `orgmemory` binary locally,
   then publishes through npm Trusted Publishing with provenance.
4. Verify `npm view @orgmemory/cli@<version> --json`, registry integrity and
   provenance, then execute the exact registry version through `npm exec`.
5. Only then may the product or public docs render a pinned `npx` command for
   that verified version.

Later versions run only through the protected workflow. If any registry,
provenance, version, commit, or ownership proof is missing, do not publish and
do not activate the command in the UI.
