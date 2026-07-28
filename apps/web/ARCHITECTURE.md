# Product Web Architecture

`apps/web` is the authenticated OrgMemory product application. It is a Vite 8,
React 19, TypeScript, Tailwind 4 application deployed as static assets behind
the unprivileged Nginx image defined in this directory.

## Boundaries

- `src/features`: product and domain-specific UI composition.
- `src/components/ui`: local shadcn/Radix primitives.
- `src/components/layouts` and `src/components/patterns`: shared product
  composition.
- `src/lib/hey-api`: generated REST client output from
  `contracts/openapi.json`.
- `test/e2e`: browser authentication and product-journey coverage.

The root pnpm workspace owns installation and the lockfile. The web package owns
its dependencies and scripts. It does not share runtime code with the separate
Fumadocs application unless a future measured reuse case justifies a package
boundary.
