---
name: orgmemory-architecture-challenge
description: Run the independent architecture challenge that CLAUDE.md requires before material decisions (domain boundaries, authorization, persistence, publication, concurrency, cache isolation, parity scope, deployment). Use when such a decision is proposed and before implementing it. Do not use for routine code review, for decisions already recorded in docs/decisions, or as a substitute for the automated security review.
---

# OrgMemory Architecture Challenge

CLAUDE.md mandates an independent architecture challenge before material
decisions are implemented, and mandates recording the proposal, strongest
counterargument, repository evidence, final choice, and rejected alternative.
This skill is the operational procedure that has actually worked: an
adversarial, read-only reviewer driven by a written brief, with escalation
rounds until the verdict is trustworthy.

## Purpose

Prevent plausible-but-wrong architecture from shipping by forcing an
independent reviewer to attack the proposal against repository evidence, and
leave a durable verdict artifact.

## Trigger Conditions

- A decision touches domain boundaries, authorization, persistence,
  publication, concurrency, cache isolation, parity scope, or deployment.
- The user asks to "judge", "challenge", or "review the architecture" of a
  design, or an increment's design.md contains such a decision.

## Required Context

- The active increment directory (`docs/increments/active/<slug>/`) or the
  decision being made on main.
- Pinned reference checkouts for comparable-system evidence (see
  `orgmemory-reference-study`): `tmp/onyx`, `tmp/upstream-*`.
- A way to dispatch a fresh session that has no stake in the proposal
  (separate agent/terminal/worktree).

## Procedure

1. **Write a challenge brief file** — `challenge-brief.md` in the active
   increment (pattern: `docs/increments/active/2026-07-29-observability-pipeline/challenge-brief.md`).
   It must contain:
   - adversarial framing: the reviewer's job is to attack the proposal, not
     validate it, and to verify claims in the code itself;
   - one paragraph on what OrgMemory is and the product promise at stake;
   - the exact rule/design under review, quoted, with the file paths that
     enforce it today;
   - evidence from comparable systems, read from source where possible, as a
     table with file-level citations;
   - observed operational cost or incident that motivated the question.
2. **Dispatch a fresh independent reviewer** pointed at the brief **file**, not
   an inline prompt (the user corrected this explicitly: write the prompt to a
   file and tell the agent to read it). The session must be read-only: no
   edits, no mutations, no plan mode. Require reading CLAUDE.md,
   docs/conventions.md, the relevant domain spec, and decision filenames.
3. **Demand a structured verdict:** explicit verdict, must-fix list, and
   repository evidence for every claim — not general agreement.
4. **Run a counterattack round** if the verdict agrees too easily: reply
   "challenge your verdict with three concrete contradictions" (with the
   contradictions you suspect). Keep rounds going until the verdict survives
   contradiction or changes for stated reasons.
5. **Record the outcome** in `challenge-verdict.md` beside the brief: date,
   commit SHA reviewed, committed recommendation, and scope limits of the
   verdict. Consolidate the rationale into the increment design or a
   `docs/decisions` entry per CLAUDE.md, including the rejected alternative.
6. **If the configured reviewer is unavailable,** record that fact and obtain
   explicit project-owner direction before proceeding (CLAUDE.md rule).

## Verification

The challenge is complete when `challenge-verdict.md` exists with a commit SHA
and a committed recommendation, the design/decision records the strongest
counterargument and rejected alternative, and any must-fix items are either
implemented or explicitly deferred by the project owner.

## Failure Handling

- **Reviewer validates instead of attacks:** rerun with the contradiction
  round; provide concrete suspected failure scenarios.
- **Reviewer output renders blank or truncated:** ask it to repeat the complete
  review as plain Markdown, no tools, no formatting extras (this recovery was
  needed and worked).
- **Reviewer cannot use tools:** fall back to a no-tools judge — distill
  verified repo facts into the prompt, state they are authoritative, and ask
  for the verdict on those facts alone.
- **Two reviewers disagree, or two designs are both defensible:** escalate to
  `orgmemory-agent-debate` — a structured two-architect debate with a no-tools
  judge that commits to one outcome from the debate record alone.
