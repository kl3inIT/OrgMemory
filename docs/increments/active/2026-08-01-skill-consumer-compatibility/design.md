# Skill consumer compatibility

Date: 2026-08-01
Status: accepted after independent challenge.

## Problem

OrgMemory already resolves an exact released Skill, verifies its immutable
package, installs it into Claude Code or Codex, and writes a token-free receipt.
The released Skill page nevertheless presents one large generic handoff panel.
It does not explain which consumer adapters OrgMemory supports, where each
adapter installs the package, or the limit of the guarantee.

That omission risks conflating three different claims:

1. the released package bytes are authentic and reproducible;
2. OrgMemory has a deterministic installation adapter for a consumer;
3. the consumer will execute the Skill correctly for every task.

Only the first two are currently enforced. The third is not certified.

## Proposal under challenge

Keep the immutable Agent Skills package as the canonical artifact. Model
Claude Code and Codex as feature-scoped consumer descriptors in the Assets web
application. Each descriptor names the consumer, its project-local install
path, exact CLI target, and support statement.

On a released Skill detail, replace the always-expanded install handoff with a
compact `Install with...` action. Selecting Claude Code or Codex opens a
target-specific dialog containing the exact-version agent prompt, exact CLI
command, confirmation boundary, prerequisites, and completion receipt.

The UI will make the guarantees explicit:

- **Verified package**: OrgMemory authenticates and verifies the immutable
  release and every file.
- **Install supported**: the official CLI has a deterministic adapter for the
  selected consumer.
- **Runtime behavior not certified**: OrgMemory does not claim that a consumer
  will select or execute the Skill correctly for every task.

No backend, API, MCP, persistence, authorization, package format, or CLI
behavior changes in this increment.

## Strongest counterargument

A consumer compatibility layer may be premature product taxonomy. The CLI
already exposes `--agent claude-code|codex`; a dropdown could simply render
those commands without introducing a descriptor or support vocabulary. New
terms may imply a formal certification program that OrgMemory does not operate,
and a dialog may hide installation information that is currently visible.

## Repository evidence

- `apps/cli/src/install.ts` owns the two deterministic destination adapters and
  token-free install receipt.
- `apps/cli/src/index.ts` exposes exact-version `orgmemory skill add`.
- `apps/web/src/features/assets/agent-handoff/skill-agent-handoffs.ts` builds
  both target commands but does not model their support semantics.
- `apps/web/src/features/assets/components/agent-handoff-panel.tsx` displays
  both consumers inside one always-expanded surface.
- `apps/web/src/features/assets/components/asset-detail-page.tsx` labels the
  package verification contract but does not distinguish install support from
  runtime behavior.
- `docs/specs/domains/asset-registry.md` records exact release resolution and
  verified installation as current behavior.

## Reference evidence

| System | Evidence | Architectural lesson |
| --- | --- | --- |
| Agent Skills | `tmp/skill-registry-research/agentskills/README.md` and client integration guidance | Keep the Skill directory portable; each client chooses its discovery or installation mechanism. |
| Vercel Skills | `tmp/skill-registry-research/skills/src/agents.ts` and install code | Consumer-specific directories are adapters around one canonical Skill package. |
| ClawHub | `tmp/skill-registry-research/clawhub` registry and install paths | Registry provenance/version resolution and runtime execution are separate responsibilities. |
| AgentRegistry | `tmp/skill-registry-research/agentregistry` Skill source resolution | Resolve a published artifact to immutable source rather than a mutable latest URL. |
| 9Router | `tmp/skill-registry-research/9router` raw URL import flow | Copy/paste acquisition is useful convenience, not an enterprise provenance or authorization contract. |

## Scope

- feature-scoped consumer descriptors for Claude Code and Codex;
- compact `Use with...` selector on released Skill detail;
- target-specific handoff dialog using existing OrgMemory tokens and shadcn
  primitives;
- precise support and guarantee copy;
- focused unit, component, browser, spec, test-matrix, and public-doc coverage.

## Non-goals

- no generic agent runtime or agent orchestration;
- no Claude web/Desktop, MCP-only, GitHub, or raw-URL adapter claim;
- no automatic command execution from the browser;
- no new installation destination or CLI option;
- no rating, usage count, marketplace, or semantic Skill certification;
- no second Skill catalog outside the governed Assets surface.

## Final choice

The independent verdict in `challenge-verdict.md` accepted the architecture
with narrower semantics. The CLI remains authoritative. A web descriptor may
project only the consumer display name, exact `--agent` value, default
project-local installation directory, and deterministic-install status.

Use exactly **Verified package**, **Install supported**, and **Runtime behavior
not certified**. The action is **Install with...**, because **Use with...**
would imply a runtime guarantee the system does not provide. A selected-target
prompt names the target and exact command rather than asking the user to choose
again.

Separate consumer-specific package variants are rejected because the only
established difference is the installation directory. Rendering unrelated
label, path, and command literals without one descriptor is also rejected
because it permits avoidable UI/CLI drift.

## Delivery

One merge-commit PR below 100 changed files. Preserve commits; do not squash.
