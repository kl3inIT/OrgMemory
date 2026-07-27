# OrgMemory CLI

Authenticated exact-version Skill installation for Claude Code and Codex.

## Requirements

- Node.js 24 or later
- an OrgMemory account allowed to use the released Skill
- a browser for the first OAuth Authorization Code + PKCE sign-in

## Commands

```text
orgmemory skill search onboarding
orgmemory skill search onboarding --json
orgmemory skill add people/employee-onboarding@1.0.0 --agent claude-code
orgmemory skill add people/employee-onboarding@1.0.0 --agent codex
orgmemory skill list
orgmemory skill list --json
```

Project-local installation is the default. Pass `--global` to target the
current user's agent directory. Use `--server` only to select another trusted
OrgMemory deployment; production defaults to `https://om.kl3in.tech/mcp`.
Use `--oauth-callback-port` to select another loopback callback port when the
default `53682` is unavailable. `--json` is supported by `skill search` and
`skill list` for machine-readable output.

The first command opens the browser for OrgMemory sign-in and consent. The CLI
persists OAuth registration/discovery/tokens in the operating system's user
state directory, not in the repository. Skill lock receipts contain exact
release and digest metadata, never credentials.

## Development

```text
pnpm install --frozen-lockfile
pnpm test
pnpm typecheck
pnpm build
pnpm link --global
```

The package is not published to npm by this increment. Publishing a public CLI
release is an explicit release operation with its own provenance and signing
policy.
