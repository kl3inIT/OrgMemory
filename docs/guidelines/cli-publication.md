# OrgMemory CLI Publication

`@orgmemory/cli` is a public transport binary for authenticated OrgMemory
operations. Public package visibility does not make the OrgMemory product,
Skill registry, or governed Skill content open source.

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

npm Trusted Publishing can only be configured after a package exists. The
first public version is therefore a project-owner bootstrap, not a CI shortcut:

1. Verify that the project owner controls the npm `orgmemory` organization and
   that the expected maintainers are the only owners. An `E404` response does
   not prove namespace ownership.
2. From the exact reviewed, green `main` commit, use Node 24 and run the frozen
   install, `pnpm check:cli`, and `npm pack --dry-run --json`.
3. Inspect the tarball list and publish `apps/cli` as public with provenance
   using short-lived interactive npm authorization. Never place that credential
   in the repository or GitHub Actions.
4. Verify `npm view @orgmemory/cli@<version> --json`, its registry integrity and
   provenance attestation, then execute the exact version through `npm exec`.
5. Configure npm Trusted Publishing for `kl3inIT/OrgMemory` and
   `.github/workflows/publish-cli.yml`, require approval on the
   `npm-production` environment, and revoke/logout the bootstrap credential.
6. Only then may the product or public docs render a pinned `npx` command for
   that verified version.

Later versions run only through the protected workflow. If any registry,
provenance, version, commit, or ownership proof is missing, do not publish and
do not activate the command in the UI.
