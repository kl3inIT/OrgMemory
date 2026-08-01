# OrgMemory CLI

This publicly downloadable transport CLI remains proprietary. See `LICENSE`
for the limited authorized-use terms included in the npm package.

Authenticated Skill authoring, Draft publication, and exact-version
installation for Claude Code and Codex.

## Requirements

- Node.js 24 or later
- an OrgMemory account allowed to use the released Skill
- a browser for the first OAuth Authorization Code + PKCE sign-in

## Commands

```text
orgmemory skill search onboarding
orgmemory skill search onboarding --json
orgmemory skill validate ./skills/expense-review
orgmemory skill publish ./skills/expense-review --namespace finance --knowledge-space <uuid>
orgmemory skill publish ./skills/expense-review --namespace finance --knowledge-space <uuid> --dry-run
orgmemory skill add people/employee-onboarding@1.0.0 --agent claude-code
orgmemory skill add people/employee-onboarding@1.0.0 --agent codex
orgmemory skill verify people/employee-onboarding --agent codex
orgmemory skill update people/employee-onboarding --to 1.1.0 --agent codex
orgmemory skill remove people/employee-onboarding --agent codex
orgmemory skill list
orgmemory skill list --json
```

Project-local installation is the default. Pass `--global` to target the
current user's agent directory. Use `--server` only to select another trusted
OrgMemory deployment; production defaults to `https://om.kl3in.tech/mcp`.
Use `--oauth-callback-port` to select another loopback callback port when the
default `53682` is unavailable. `--json` is supported by validation,
publication, search, list, and verification commands for machine-readable
output.

`skill validate` and `skill publish --dry-run` are offline. They validate the
root `SKILL.md`, bounded file tree, and deterministic package without signing
in. A real `skill publish` requests the separate `assets:write` OAuth scope and
creates a Draft only. Submit, review, approval, and release remain in OrgMemory
Governance.

The first command opens the browser for OrgMemory sign-in and consent. The CLI
persists OAuth registration/discovery/tokens in the operating system's user
state directory, isolated by server and scope set, not in the repository. Skill lock receipts contain exact
release and digest metadata, never credentials.

Every installation receipt records the exact regular-file set. `skill verify`
is offline and reports `verified`, `modified`, `missing`, or `unverifiable`.
Updates keep the same coordinate, require an explicit destination version, and
re-authorize that release. Update and removal refuse locally changed or legacy
schema-v1 installations. There is deliberately no destructive `--force` path;
preserve or clean up such content manually. Lifecycle mutations are serialized
per project/global scope and use a durable recovery journal so a later command
can restore an interrupted tree-plus-receipt transaction.

## Development

```text
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm build
pnpm link --global
```

The package is prepared for public npm distribution, but a consumer command is
not considered active until that exact version exists in the registry with
verified provenance. The first publication follows
`docs/guidelines/cli-publication.md`; later releases use the protected manual
Trusted Publishing workflow. Once activated, consumers should use an exact
package version rather than a mutable tag:

```text
npx --yes @orgmemory/cli@<cli-version> skill add people/employee-onboarding@1.0.0 --agent codex
```
