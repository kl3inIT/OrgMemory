# Public docs hydration repair

## Problem

The deployed documentation renders its content but logs React production error
`#418` on both `/docs/getting-started` and
`/docs/product-guides/work-with-governed-assets`. Browser evidence points to a
server/client hydration mismatch rather than a failed request or missing page.

The mismatch appeared after `816a3438` moved `<body>` into a Client Component
that derives its category class directly from `useParams()`. The server can
pre-render the fallback `getting-started` class while the browser's first render
already sees the actual slug, so React receives different initial body markup.

## Selected repair

Keep the current body-level category contract because the shell, sidebar, and
page all inherit `--color-fd-primary` from it. Make the first server and client
render deterministic: initialize the Client Component with the stable
`getting-started` fallback, then synchronize the actual route category after
mount. This follows the Next.js guidance to render a stable fallback and update
the pathname-dependent fragment in an effect.

Do not use `suppressHydrationWarning` on `<body>`. That would hide the mismatch
rather than remove it. Do not move the class to page content because the docs
shell would stop inheriting the category color.

## Verification

- Add a production-browser regression that fails on React hydration error
  `#418` or the development hydration diagnostic.
- Preserve the existing body-category and category-color tests.
- Run Node 24 docs checks, production build, focused browser tests, full docs
  browser tests, release policy, and live browser verification after automatic
  deployment.

## Scope

No content, navigation, Fumadocs information architecture, deployment topology,
or product runtime changes.
