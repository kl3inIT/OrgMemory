# Public docs hydration repair verification

## Outcome

PR [#247](https://github.com/kl3inIT/OrgMemory/pull/247) merged with merge
commit `df856ab8e4329a1f05494733383acc4cba68a574`. Fumadocs Core and Base UI
now use 16.14.0 together, Fumadocs MDX uses 15.2.1, and the public sidebar
category selector hydrates without React error `#418`.

The initial route-dependent body hypothesis was disproved by comparing the
static Linux DOM with the hydrated DOM. The first structural difference was
Fumadocs 16.13 omitting the active category trigger during static generation
and adding it during the browser's first render. The aligned 16.14 release
removed that difference without an application workaround or dependency patch.

## Verification evidence

| Gate | Result |
| --- | --- |
| Node runtime | Node 24.15.0 |
| Docs checks | `pnpm --filter @orgmemory/docs check` passed |
| Production build | `pnpm --filter @orgmemory/docs build` passed, 147 static pages generated |
| Browser suite | `pnpm --filter @orgmemory/docs test:e2e`: 31 passed, 3 intentionally skipped |
| Release policy | `pnpm release:check` passed |
| Linux image | Repository Dockerfile built and served healthy; four representative routes emitted zero console and hydration errors |
| PR CI | Run `30710840581` passed, including Public docs Node 24 and CI Gate |
| Post-merge CI | Run `30710967952` passed on the merge SHA |
| Docs image | Run `30711066758` built, scanned, recorded, and published the immutable image |
| Docs deployment | Run `30711185786` deployed and verified the exact image |

CodeRabbit could not start a review because the account was rate-limited; its
API returned no inline findings. The dependency diff, regression, full local
gates, PR CI, post-merge CI, image scan, and deployment all passed.

## Live proof

After automatic deployment, Playwright opened the following uncached routes on
`https://docs.kl3in.tech`:

- `/docs/getting-started`
- `/docs/product-guides/work-with-governed-assets`
- `/docs/architecture-security/authorization`
- `/vi/docs/getting-started`

Every route returned HTTP 200, rendered the expected localized category
selector and body category class, and produced zero console errors and zero
React hydration diagnostics.

## Durable invariant

Keep Fumadocs Core and Base UI on the same release. The production-browser suite
must continue to fail on React hydration diagnostics for representative public
routes.
