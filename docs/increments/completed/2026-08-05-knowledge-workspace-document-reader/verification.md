# Knowledge Workspace And Document Reader Verification

## Outcome

The employee-facing workspace is now named Knowledge, with Documents and
Knowledge graph retained as peer surfaces on the compatible `/sources` route.
Documents open in a responsive right-side evidence reader that preserves list
context on desktop and uses the available width on narrow screens.

The reader safely presents Markdown, PDF, raster images, and plain text while
keeping Office and unsafe formats download-only. Markdown reuses the restricted
Assistant renderer in static mode with Rendered and Raw views. Preview failures
offer Retry, classification no longer invents an effective audience, and the
employee list no longer exposes index-profile implementation detail.

## Automated Evidence

Verified on Node `v24.15.0`:

- `corepack pnpm lint`: passed.
- `corepack pnpm typecheck`: passed.
- `corepack pnpm test:unit`: 20 files and 67 tests passed.
- `corepack pnpm exec playwright test --workers=4`: 31 tests passed.
- `corepack pnpm build`: passed.
- `corepack pnpm release:check`: 18 Tegami contract tests and 23 release-policy
  tests passed; the release-management check passed.
- `python scripts/check_docs.py`: 508 Markdown files and 8 mirrored domain
  pairs passed.
- `git diff --check`: passed.

The first default-parallel Playwright run passed 29 tests and timed out in two
pre-existing Assistant reload tests. Both passed immediately in a focused
single-worker rerun, and the complete 31-test suite then passed with four
workers. The new cross-format Knowledge journey passed in every run.

## Browser And Format Evidence

Deterministic browser fixtures prove:

- Knowledge is the enclosing navigation label and Documents/Knowledge graph
  are peer tabs.
- Markdown delivered as safe plain text is rendered only when canonical source
  metadata declares Markdown; active HTML and remote images remain blocked.
- PDF and safe raster images render inline, plain text remains readable, and
  Office evidence stays download-only.
- Preview retry recovers from a failed fetch.
- The desktop reader preserves list context and the mobile viewport has no
  horizontal page overflow.

Manual review of the captured desktop Markdown reader and narrow mobile reader
confirmed the final post-animation geometry, metadata wrapping, toolbar access,
and viewport-filling reading surface.

## Scope And Remaining Risk

No backend Java, API, persistence, ingestion, or authorization contract changed,
so Gradle and IDE Java inspection were not applicable. Browser coverage uses
deterministic authorized fixtures rather than a production deployment. Vite
still reports the repository's non-blocking large-chunk advisory, including the
shared restricted Markdown bundle; bundle splitting remains separate work.
