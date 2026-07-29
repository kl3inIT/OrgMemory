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

- PR #135 merged the deployment-smoke repair as
  `bd51abab87bff29f540b4973fc7dd4d2078e1db4`. PR CI run `30465222761` and
  main CI run `30465637980` passed, including deployment contracts,
  forced-canary rollback, and the aggregate CI Gate.
- Immutable docs build, scan, release recording, and publication passed in run
  `30465984776`. Deploy run `30466105695` started a healthy container, passed
  stable smoke, verified 24 allowlisted routes and five public outputs, and
  recorded exact revision `bd51abab87bff29f540b4973fc7dd4d2078e1db4`.
- Independent live verification confirmed:
  - `/` returns `307` to `/docs/getting-started`;
  - legacy EN, VI, HTML, and Markdown paths return `308` to their canonical
    taxonomy paths;
  - all four category routes return HTML 200 with distinct category body
    classes and primary colors;
  - the Vietnamese mobile switcher exposes all four localized categories and
    untranslated pages retain the explicit English-fallback notice;
  - the sitemap contains the new allowlist and excludes retired paths;
  - explicit `.md` siblings return Markdown;
  - security and immutable-static-cache headers remain present;
  - the live browser reported no console errors; and
  - `https://om.kl3in.tech/healthz` remained `200 ok`.

The taxonomy foundation is complete. The active program now pauses at the
owner-context checkpoint for queue item 1, **What is OrgMemory?**.
