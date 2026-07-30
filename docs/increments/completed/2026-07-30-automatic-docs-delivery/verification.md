# Automatic Public Docs Delivery Verification

## Outcome

Public docs now follow an independent automatic release train:

```text
trusted successful CI push on main
  -> exact-commit docs image publication
  -> verified release artifact
  -> docs-only production deployment
  -> health, smoke, and bilingual publication crawl
```

Manual image publication remains non-deploying. Manual exact-commit deployment
still requires explicit confirmation and remains available for redeploy or
rollback.

## Delivery Evidence

[PR #148](https://github.com/kl3inIT/OrgMemory/pull/148) merged the automatic
workflows as `0b7b2ac5570e5377085cb01d6a949f9d09413952`.

- merge CI run `30523752238` passed, including Public docs, deployment
  contracts, backend regression, and CI Gate;
- automatic image run `30523943003` published the exact SHA image and release
  artifact;
- automatic deployment run `30524096252` reached a healthy candidate and
  passed docs smoke, then rejected it because the production verifier filtered
  only English sitemap paths while the manifest now contained explicit
  Vietnamese routes;
- the failed-canary path restored image
  `sha-605c872c0e4353011f92715ced9833c94e29ab11`, returned the service to
  healthy state, reran smoke successfully, and exited non-zero.

[PR #149](https://github.com/kl3inIT/OrgMemory/pull/149) repaired the bilingual
publication contract as `8cf5036935c67e67e38a58066bffcf6f9cfe154c`.
CodeRabbit reviewed the workflow, verifier, and regression test and produced no
actionable comments.

- PR CI run `30524702546` passed all selected jobs and CI Gate;
- merge CI run `30524968631` passed;
- automatic image run `30525245076` published the exact immutable image and
  release artifact;
- automatic deployment run `30525367863` validated the artifact, deployed only
  `orgmemory-docs`, reached healthy state, and passed smoke plus the full
  publication crawl.

No workflow dispatch was used for either automatic image or deployment run.

## Verification Gates

Local and PR gates passed:

- actionlint `1.7.12`;
- repository documentation operating-model check;
- Node `24.15.0` public-docs contracts and Next.js production build;
- publication verifier compile and two focused localization tests;
- local production publication crawl: 50 allowlisted English/Vietnamese routes
  plus five public outputs;
- deployment Compose, failed-canary rollback, shell, Dockerfile, web
  dependency, Keycloak onboarding, adapter, frontend, backend, and CI Gate
  checks.

Independent live verification after deployment passed:

- `https://docs.kl3in.tech/healthz`;
- English and Vietnamese Getting Started;
- English and Vietnamese Core Concepts;
- English and Vietnamese First Governed Journey;
- English and Vietnamese Work with Governed Assets;
- all 50 allowlisted English/Vietnamese routes and five public outputs.

Every named route returned HTTP `200`. The docs release remained isolated from
the product services, used the protected production environment, and removed
its run-scoped registry credentials after the remote transaction.

## Security And Ordering Properties

- Automatic publication accepts only successful same-repository `push` CI runs
  on `main`; pull-request, fork, and manually dispatched CI runs are excluded.
- A successful CI run with a skipped Public docs job publishes no image.
- A manual docs image build never initiates automatic live deployment.
- Deployment requires the exact triggering publish job and verifies the
  release artifact commit, immutable image reference, and digest before
  entering the production environment.
- A newer published descendant docs image supersedes an older build; a later
  non-docs commit does not suppress the last verified docs release.
- Package-write and package-read permissions exist only on their respective
  publish and deploy jobs.

The increment exit gate is satisfied.
