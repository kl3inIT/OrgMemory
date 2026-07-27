# Skill Registry Distribution

Status: completed by PR #88.

## Problem

OrgMemory can govern and release a Skill package, but an authorized employee
cannot yet inspect its install contract or install the exact release into an AI
coding agent. The generic Asset detail also routes `SKILL` through the
Capability Pack panel, which is the wrong product behavior.

Making users download and upload ZIP files between products would expose an
implementation artifact as the workflow. It would also lose the release,
digest, organization, target-agent, and installation receipt needed for
reproducibility.

## Outcome

- Expose an authenticated, permission-aware Skill manifest for one exact
  released Asset.
- Stream the original exact package without exposing its object-storage key.
- Add `get_skill_manifest` to MCP and include `SKILL` in Asset discovery.
- Add an OrgMemory Node CLI with:
  - `orgmemory skill search <query>`;
  - `orgmemory skill add <namespace/slug>@<version> --agent
    claude-code|codex`;
  - `orgmemory skill list`.
- Authenticate the CLI against the existing MCP protected resource through
  OAuth Authorization Code, PKCE S256, and restricted DCR. Do not ask users to
  paste bearer tokens or client secrets.
- Verify the archive digest and every file against the released manifest,
  install through an adjacent staging directory, and write an exact local lock
  receipt only after an atomic replacement succeeds.
- Render a Skill-specific Asset detail with metadata, file manifest, digest,
  version, and copyable install commands. The browser does not make manual ZIP
  download the primary action.

## Installation locations

Project scope is the default:

| Agent | Project Skill location |
| --- | --- |
| Claude Code | `.claude/skills/<skill-name>` |
| Codex | `.agents/skills/<skill-name>` |

The target is derived from a fixed agent map and the validated Skill name. No
server field or archive path may select an arbitrary local filesystem path.

## Delivery boundaries

```text
Web session ────────────────> canonical API ──> Asset Registry + object storage

CLI ──OAuth/PKCE──> MCP resource
  ├─ MCP tools ─────────────> canonical API ──> live CAN_USE
  └─ package endpoint ──────> canonical API ──> live CAN_USE + exact blob
```

- `core` owns live `CAN_USE`, release type validation, manifest construction,
  blob-reference integrity, storage integrity, and audit.
- `apps/api` exposes the canonical manifest and streaming package contract.
- `apps/mcp` remains an adapter: it exchanges the inbound MCP-audience token
  and never reads Asset persistence or object storage directly.
- The CLI calls MCP tools for discovery and manifest data. It downloads bytes
  from a bearer-protected endpoint on the same MCP resource so a 20 MiB archive
  is not expanded into base64 JSON-RPC.
- Browser session auth and CLI OAuth remain separate. Tokens never enter the
  Skill lock file.

## OAuth state

The CLI reuses the MCP TypeScript SDK OAuth discovery and provider contract.
Public DCR client information and OAuth tokens are stored under the current
user's OrgMemory state directory with restrictive file permissions. The state
is isolated by MCP server origin and authorization-server issuer. It is never
written into a repository.

This avoids registering a new DCR client on every command and allows normal
refresh-token reuse. The remaining platform limitation is that Windows user
profile ACLs, rather than POSIX mode bits, are the effective protection for the
state file.

## Package integrity and atomicity

Installation must fail before modifying the active target when any of these is
false:

- the Asset and release are currently usable and have type `SKILL`;
- payload artifact metadata equals the release blob reference;
- opened object metadata equals the release blob reference;
- downloaded byte length and SHA-256 equal the manifest;
- extracted paths and the exact set of files equal the manifest;
- every extracted file SHA-256 and size equal the manifest.

The installer writes to an adjacent random staging directory. If replacing an
existing install, it moves the old directory to a backup, promotes staging, and
rolls back the backup when promotion fails. The lock receipt is written through
its own temporary file and rename only after promotion.

## Scope decisions

- No public marketplace, ratings, social publishing, arbitrary Git execution,
  mutation MCP tools, or automatic agent execution.
- No browser-first ZIP import or download flow.
- No generic plugin registry in this increment. A Skill remains a standalone
  Asset; multi-skill/plugin packaging is a later profile.
- No Fable 5 review in this increment, following the project owner's explicit
  waiver for this implementation sequence. The strongest counterargument is
  that a dedicated installer protocol could be simpler than coupling discovery
  to MCP. The decision is to reuse MCP OAuth and identity because it eliminates
  a second public OAuth resource while the binary endpoint remains a small,
  bounded transport adapter.
