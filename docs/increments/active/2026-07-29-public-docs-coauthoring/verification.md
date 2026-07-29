# Public Documentation Co-Authoring Verification

## Taxonomy Migration

- Frozen workspace install completed without changing the lockfile.
- Sanitized OpenAPI generation produced 110 paths in seven endpoint groups
  under `Reference`.
- `pnpm --filter @orgmemory/docs check` passed for API generation, lint,
  types, content, manifest, publication policy, route boundaries, and links.
- Production build generated 153 static pages.
- Playwright passed 23 tests across desktop and mobile Chromium, with three
  deliberate single-project skips. Coverage includes the four-category
  switcher and visual identities, English/Vietnamese shells, permanent legacy
  redirects, accessibility, search, Markdown siblings, security headers,
  sitemap completeness, and public-output safety.
- The root `corepack pnpm docs:dev` command started the independent docs
  application at `http://localhost:3000`; `/healthz` returned HTTP 200 with
  body `ok`. No product service or Compose dependency was started.

Local verification ran on Node 23.11.1 and therefore emitted the expected
engine warning. The repository requires Node 24 or newer; GitHub Actions on the
required version remains the merge authority.

## Delivery

- PR #134 merged to `main` as
  `e2a376358048b85d465532e4f3f8a4bfddeac138`.
- PR CI run `30464083979` and main CI run `30464387882` passed, including the
  Node 24 public-docs job and aggregate CI Gate.
- Immutable image build, scan, release recording, and publication passed in run
  `30464628816`.
- Initial deploy run `30464858925` started a healthy candidate, then safely
  restored `fb1c176b9503a0692a0781b0fa924ac7334e58d3`. The deployment smoke still
  expected the retired `/docs/overview` root, and rollback incorrectly compared
  the restored old image with the candidate manifest.
- The follow-up repair makes smoke checks taxonomy-stable and limits the
  candidate-manifest publication audit to the candidate. Forced-canary rollback
  now proves the previous image is checked without applying the candidate
  manifest to it.

Pending repair PR, immutable rebuild, successful deployment, and live
verification.
