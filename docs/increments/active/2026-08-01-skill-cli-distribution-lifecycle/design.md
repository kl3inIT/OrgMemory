# Skill CLI distribution and local lifecycle

Date: 2026-08-01
Status: accepted with changes after independent challenge.

## Problem

OrgMemory already resolves an exact authorized Skill release, verifies its
immutable package, installs it atomically for Claude Code or Codex, and writes
a token-free local receipt. The consumer path still has two material gaps:

1. the product UI prints an `orgmemory` command even though the CLI is not
   published, so a new user has no reproducible way to obtain that binary;
2. the receipt can be listed, but it cannot prove that installed files remain
   unchanged or support an explicit safe update/removal lifecycle.

This leaves users with a successful first installation but no governed answer
for local drift, upgrade, or cleanup.

## Proposal under challenge

Publish the existing Node 24 CLI as the public scoped npm package
`@orgmemory/cli`. Keep CLI and product versions independent: package consumers
pin the exact CLI version, while the CLI continues to require an exact Skill
release coordinate. Publish only from a dedicated GitHub Actions workflow using
npm Trusted Publishing after a green `main` commit and an approved GitHub
environment. The workflow must use OIDC, no write token, Node 24, npm 11.5.1 or
newer, no dependency cache, and public-package provenance.

The first publication is a controlled bootstrap. Current npm 11 allows the
authenticated package owner to preconfigure the GitHub trusted publisher with
`npm trust github`, even before the package exists. The owner must create or
control the `@orgmemory` npm organization and establish that trust, but the
package itself is published only by the same OIDC workflow used for subsequent
versions; no interactive publish token is introduced.

Upgrade the receipt to schema version 2. Each installed entry records the
verified package manifest file paths, sizes, and SHA-256 digests. Enforce one
receipt owner per canonical consumer target. Add:

- `skill verify [reference]`: offline comparison of receipt metadata and the
  installed tree, with `verified`, `modified`, `missing`, and `unverifiable`
  outcomes;
- `skill update <installed-reference> --to <exact-version>`: authenticated
  exact-version replacement through the existing verified, atomic installer,
  preserving the installed consumer and project/global scope;
- `skill remove <reference>`: offline deletion only after verification;
  modified and legacy-unverifiable installs require manual cleanup or a
  verified reinstall first. There is no destructive `--force` path.

`update` never resolves `latest`. All mutating commands serialize per
project/global scope with an exclusive cross-process lock. A durable operation
journal, staging tree, and backup/quarantine make an interrupted tree-plus-
receipt transaction recoverable before a later lifecycle command proceeds.
Targets are recomputed from scope, consumer, and receipt identity rather than
trusted from receipt text. The CLI does not execute Skill content.

After the npm package is live, released-Skill handoffs use a pinned command:

```text
npx --yes @orgmemory/cli@<cli-version> skill add <namespace>/<slug>@<skill-version> --agent <consumer>
```

## Strongest counterargument

Publishing a public CLI introduces a second release train and supply-chain
surface before OrgMemory has many consumers. A browser download or repository
checkout could avoid npm organization setup, version synchronization, OIDC
configuration, provenance verification, and local destructive-command risk.
Moreover, destructive removal can erase user edits, and a local receipt cannot
prove that a downstream agent actually discovers or executes the installed
Skill.

## Repository evidence

- `apps/cli/package.json` already defines the `@orgmemory/cli` package, Node 24
  engine, and `orgmemory` binary, but marks it private.
- `apps/cli/src/install.ts` verifies the package and every manifest file,
  protects extraction paths, promotes through staging/backup, and writes a
  schema-v1 receipt.
- `apps/cli/src/index.ts` exposes exact-version `skill add` and receipt-only
  `skill list` but no verify, update, or remove commands.
- `apps/web/src/features/assets/agent-handoff/skill-agent-handoffs.ts` emits a
  bare `orgmemory` command.
- `docs/specs/domains/asset-registry.md` lists update/remove and public npm
  publication as future work.
- `.github/workflows/release.yml` owns whole-product releases and does not
  publish workspace packages.

## Reference evidence

| System | Pinned source | Lesson retained | Limit of comparison |
| --- | --- | --- | --- |
| Vercel Skills | `tmp/skill-registry-research/vercel-skills/README.md`, `src/local-lock.ts`, `src/update.ts`, `src/remove.ts` at `1164afa5f0e21ebd01e6fc11249759353f494ad1` | `npx`, project-local lock data, content hashing, and explicit update/remove form a usable local lifecycle. | Its sources are mostly public repositories and its delete path is not OrgMemory's governed authorization boundary. |
| ClawHub | `tmp/skill-registry-research/clawhub/README.md`, `packages/clawhub/src/skills.ts`, `packages/clawhub/src/cli/commands/skills.ts`, `docs/acceptable-usage.md` at `a643b75eca24d2180d7c6819d17476f278ad5e00` | Store installed provenance and a content fingerprint, stage replacements, refuse silent overwrite of locally changed content, and keep registry moderation distinct from local uninstall. | ClawHub is a public OpenClaw registry, resolves mutable latest versions by default, and its trust/telemetry model is not OrgMemory's private authorization contract. |
| Onyx | `tmp/onyx/web/src/views/SkillsPage.tsx`, `web/src/lib/skills/picker.ts` at `618b5031bf21463f44e3bed9eb9d5073b806fec0` | A central product can expose built-in, shared, personal, and enabled Skills without treating local installation as execution certification. | Onyx owns the agent runtime, while OrgMemory currently installs into external consumers. |
| AgentRegistry | `tmp/upstream-agentregistry/README.md`, `docs/declarative-cli.md`, `docs/governance/cncf/technical-review.md` at `d8d3f4ef1ebeed70d58adafd26590ead6198addf` | A registry separates curated metadata, publish/pull operations, and target runtime configuration. | Its own review says immutable agent/Skill versioning remains in progress, so OrgMemory must not copy mutable `latest` tags. |
| 9Router | `tmp/upstream-9router/src/app/landing/components/GetStarted.js`, `README.md` at `6fcd27337a7893642c7fe630840d0a641743f28f` | A copyable `npx` command removes global-install friction. | It runs one public local application; it does not manage authorized immutable Skill artifacts. |

## Scope

- publishable npm package metadata and a dedicated trusted-publishing workflow;
- receipt-v2 compatibility and deterministic local verification;
- exact-version update and safe remove commands;
- focused package/lifecycle/workflow tests;
- pinned `npx` handoff in the existing Assets surface after registry proof;
- Asset Registry spec/test matrix, architecture, CLI README, bilingual public
  Product Guide, and product release note reconciliation.

## Non-goals

- no automatic `latest` updates or background mutation;
- no local pin/unpin feature in this increment because every update already
  requires an explicit destination version;
- no new agent consumers beyond Claude Code and Codex;
- no Skill runtime, semantic execution certification, ratings, or usage counts;
- no separate marketplace or Skill workspace;
- no storage of OAuth tokens, npm tokens, or customer content in receipts;
- no MCP-based delivery to Claude web/Desktop in this increment;
- no package signing scheme beyond npm provenance and the existing Skill
  package/release digests.

## Delivery shape

One increment may use two merge-commit PRs if npm bootstrap requires it:

1. CLI lifecycle, publishable package, trusted-publishing workflow, and docs;
2. switch the product handoff to the verified live npm version.

If the npm scope is ready before integration, combine them into one PR. Every
PR stays below 100 changed files and preserves commits; never squash.

## Decision

The independent verdict is recorded in `challenge-verdict.md`. The proposal is
accepted only with its final contract: one package-owned CLI version, full-tree
receipt v2, offline fail-closed verification, same-coordinate exact updates,
verified-only removal without `--force`, one canonical target owner, scope-wide
mutation locking, durable crash recovery, dedicated approved npm publication,
and registry proof before UI activation.

## 2026-08-02 pre-publication correction

Live npm 11 verification disproved the earlier assumption that Trusted
Publishing could only be configured after a package already existed. The
accepted OIDC-only boundary is now stronger: the owner establishes trust with
`npm trust github`, then the first package is published from the protected
GitHub-hosted workflow. The packed package also carries the exact repository
identity required for provenance, and the workflow executes the local tarball's
`orgmemory` binary before any registry mutation.
