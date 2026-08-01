# Public docs hydration repair

## Problem

The deployed documentation renders its content but logs React production error
`#418` on both `/docs/getting-started` and
`/docs/product-guides/work-with-governed-assets`. Browser evidence points to a
server/client hydration mismatch rather than a failed request or missing page.

Inspection of the static Linux image disproved the initial body-class
hypothesis. The first structural difference is inside Fumadocs' sidebar header:
Fumadocs 16.13 omits the active category trigger from the static server markup
but adds it on the browser's first render. The body category is identical on
both sides and is not the cause.

## Selected repair

Keep the current body-level category contract because the shell, sidebar, and
page all inherit `--color-fd-primary` from it. Upgrade Fumadocs Core and Base UI
together from 16.13.0 to 16.14.0, plus the compatible Fumadocs MDX patch. The
aligned release makes the active layout tab available during static rendering,
so the category trigger has the same structure on the server and client.

Do not use `suppressHydrationWarning`; that would hide the mismatch rather than
remove it. Do not carry an application workaround or dependency patch when the
released upstream packages already repair the production runtime.

## Verification

- Add a production-browser regression that fails on React hydration error
  `#418` or the development hydration diagnostic.
- Preserve the existing body-category and category-color tests.
- Build the repository Dockerfile and verify English, Vietnamese, Product
  Guides, and Architecture routes with zero browser console errors.
- Run Node 24 docs checks, full browser tests, release policy, and live browser
  verification after automatic deployment.

## Scope

No content, navigation, Fumadocs information architecture, deployment topology,
or product runtime changes.
