---
name: orgmemory-agent-debate
description: Run one bounded cross-model architecture debate over two defensible OrgMemory architectures, then have the primary agent commit one consolidated decision from that single response. Use when two candidate designs are both defensible or the user asks to debate with another model. Do not use when there is one concrete proposal to attack (use orgmemory-architecture-challenge) or for routine code review.
---

# OrgMemory Agent Debate

When a decision has two genuinely defensible architectures, ask one independent
cross-model architect to defend the opposing position once. The primary agent
then consolidates that response with its own evidence and commits to exactly one
outcome. Do not prolong the exchange with rebuttal rounds or a separate judge.

## Purpose

Force a single committed architecture decision out of a genuinely contested
design space with one bounded external response and a durable primary-agent
synthesis.

## Trigger Conditions

- Two candidate architectures are both defensible and the choice is material
  (boundaries, authorization, persistence, publication, concurrency, parity
  scope, deployment).
- An `orgmemory-architecture-challenge` exposes two defensible alternatives.
- The user asks to "debate", "discuss với codex/claude", or wants a second
  model's opposing take before committing.

## Required Context

- The decision framed as two positions with the repo evidence each would cite.
- Orca CLI for spawning and driving the counterpart agent's terminal
  (`orca terminal send` / `read` / `wait --for tui-idle --timeout-ms ...`).
  Prefer a counterpart on a different model family than the driving agent.
- A place for relay files: `tmp/` (untracked reference space) or the session
  scratchpad. The external response is never committed; only the primary
  agent's consolidated decision enters `docs/`.

## Procedure

1. **Confirm debate is the right tool.** One concrete proposal → use
   `orgmemory-architecture-challenge` instead. Two live alternatives → debate.
2. **Write the debate brief to a file — never paste it inline.** The standing
   correction is explicit: create a prompt file and tell the counterpart to
   read it. The brief contains: the question, both candidate architectures,
   file paths for the evidence each side would use, hard constraints
   (read-only, no edits, no mutations), and the required output shape
   (position, attacks on the other side, concessions).
3. **Dispatch the counterpart once.** Give the independent cross-model agent
   one role — defender of B or skeptic of A — and point it at the brief file.
   Drive it through orca: send the "read `<file>` and respond into `<file>`"
   instruction, then `orca terminal wait --for tui-idle` with a timeout before
   reading. The counterpart may inspect the repo and writes one complete
   response file. Do not ask a follow-up question or request a rebuttal round.
4. **Consolidate once.** The primary agent reads the one external response,
   checks its concrete claims against already inspected evidence, and writes a
   committed synthesis. A tie is not allowed. Do not start a separate judge
   session.
5. **Record the outcome.** Write the primary synthesis to a verdict file,
   confirm it exists, then consolidate the decision — including the losing
   position as the rejected alternative — into the active design or a decision
   entry per `AGENTS.md`.

## Verification

Done when the counterpart's single response and the primary verdict exist on
disk, the verdict contains one committed decision with no hedge or tie, and the
decision plus rejected alternative are consolidated into the increment design
or a decision entry.

## Failure Handling

- **Counterpart model out of quota or unavailable:** skip the debate step
  without blocking the loop (established rule: "Claude hết quota thì bỏ qua
  bước discuss"). Fall back to a single-reviewer
  `orgmemory-architecture-challenge` with a counterattack round, and record
  that the debate was skipped; for material decisions CLAUDE.md then requires
  explicit project-owner direction.
- **Orca terminal hangs or never reaches idle:** re-run `orca terminal wait`
  with a longer timeout, re-read the terminal; if the session is dead, respawn
  it and re-point it at the same brief file — the file relay makes retries
  cheap.
- **Blank, truncated, or non-evidenced output:** record the response as unusable
  and treat the counterpart as unavailable. Do not ask it again. Continue under
  the unavailable-counterpart rule above.
- **Premature agreement:** the primary synthesis must still state the strongest
  concrete contradiction and commit to one design. Do not open another round.
