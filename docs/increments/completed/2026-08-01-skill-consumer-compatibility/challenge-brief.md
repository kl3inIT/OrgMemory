# Adversarial architecture challenge: Skill consumer compatibility

You are an independent, read-only architecture reviewer. Your job is to attack
the proposal, not validate it. Verify every claim in the repository and pinned
references. Do not edit files, create commits, or change runtime state.

Read first:

- `AGENTS.md`
- `docs/conventions.md`
- `docs/guidelines/agent-safety.md`
- `docs/specs/domains/asset-registry.md`
- decision filenames under `docs/decisions`
- `docs/increments/completed/2026-08-01-skill-consumer-compatibility/design.md`

## Product promise at stake

OrgMemory is a governed organizational memory layer. A released Skill must
remain an immutable, permission-aware organizational Asset that a user can
install reproducibly without OrgMemory pretending to be the agent runtime or
claiming that arbitrary consumers will execute the Skill correctly.

## Exact rule under review

> Keep one canonical immutable Agent Skills package. Model Claude Code and
> Codex as web feature-scoped consumer descriptors whose deterministic CLI
> adapters are labelled `Install supported`. Replace the always-expanded
> released-Skill handoff with a compact `Use with...` selector and a
> target-specific dialog. Keep `Verified package` separate and state that
> runtime behavior is not certified.

The behavior is enforced or displayed today in:

- `apps/cli/src/install.ts`
- `apps/cli/src/index.ts`
- `apps/web/src/features/assets/agent-handoff/agent-handoff.ts`
- `apps/web/src/features/assets/agent-handoff/skill-agent-handoffs.ts`
- `apps/web/src/features/assets/components/agent-handoff-panel.tsx`
- `apps/web/src/features/assets/components/asset-detail-page.tsx`
- `docs/specs/domains/asset-registry.md`

## Comparable-system evidence to verify

| System | Local source |
| --- | --- |
| Agent Skills | `tmp/skill-registry-research/agentskills` |
| Vercel Skills | `tmp/skill-registry-research/skills` |
| ClawHub | `tmp/skill-registry-research/clawhub` |
| AgentRegistry | `tmp/skill-registry-research/agentregistry` |
| 9Router | `tmp/skill-registry-research/9router` |
| Onyx | `tmp/onyx` |

## Operational motivation

The released Skill page currently displays a large generic handoff surface and
both target commands at once. The UI proves package integrity nearby but does
not explicitly delimit that proof from target installation support or runtime
behavior. The project owner also wants heterogeneous agent consumption without
duplicating the Assets registry or creating a separate marketplace.

## Required verdict

Return plain Markdown with:

1. **Verdict**: accept, accept with changes, or reject.
2. **Strongest counterargument** against the proposal.
3. **Must-fix list**, ordered by severity.
4. **Repository evidence** for every claim, with file paths.
5. **Reference evidence** and any invalid comparison.
6. **Recommended final contract**, including exact user-facing support terms.
7. **Rejected alternative** and why.
8. **Scope limits** that must remain non-goals.

Challenge your own verdict with at least three concrete failure scenarios
before finalizing it.
