# Plan

- [x] Add exact Skill manifest and verified package streaming to the canonical
  API.
- [x] Add MCP Skill discovery metadata and bearer-protected package proxying.
- [x] Add the OAuth PKCE OrgMemory CLI with search, exact add, list, atomic
  install, digest verification, and lock receipts.
- [x] Add the Skill-specific Asset detail and copyable agent install commands.
- [x] Add focused backend, CLI, and frontend tests.
- [x] Regenerate OpenAPI clients and run repository verification gates.
- [ ] Open the pull request and resolve CI and review findings.

## Verification

- `./gradlew --no-daemon clean test`
- `web`: lint, typecheck, Vitest, production build, and generated API drift
- `apps/cli`: frozen install, Vitest, typecheck, and build on the Node 24
  contract
- `actionlint` and `git diff --check`
