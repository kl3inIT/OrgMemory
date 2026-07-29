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

Pending PR review, merge, immutable build, deployment, and live verification.
