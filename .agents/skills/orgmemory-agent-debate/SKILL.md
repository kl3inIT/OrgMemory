---
name: orgmemory-agent-debate
description: Run a two-architect debate between independent agents (ideally different models, e.g. Claude vs Codex) over two defensible OrgMemory architectures, then force a committed decision from a no-tools judge that reads only the debate record. Use when two candidate designs are both defensible or a single reviewer keeps flip-flopping. Do not use when there is one concrete proposal to attack (use orgmemory-architecture-challenge) or for routine code review.
---

# OrgMemory Agent Debate

When a decision has two genuinely defensible architectures, one adversarial
reviewer is not enough — it tends to flip-flop or split the difference. The
working pattern is a structured debate: two agents defend opposing positions
against each other, and a separate judge, forbidden from touching the repo,
commits to exactly one outcome based on the debate record alone.

## Purpose

Force a single committed architecture decision out of a genuinely contested
design space, with the reasoning of both sides preserved as a durable record.

## Trigger Conditions

- Two candidate architectures are both defensible and the choice is material
  (boundaries, authorization, persistence, publication, concurrency, parity
  scope, deployment).
- An `orgmemory-architecture-challenge` reviewer keeps changing its verdict
  between rounds, or two independent reviews disagree.
- The user asks to "debate", "discuss với codex/claude", or wants a second
  model's opposing take before committing.

## Required Context

- The decision framed as two positions with the repo evidence each would cite.
- Orca CLI for spawning and driving the counterpart agent's terminal
  (`orca terminal send` / `read` / `wait --for tui-idle --timeout-ms ...`).
  Prefer a counterpart on a different model family than the driving agent.
- A place for relay files: `tmp/` (untracked reference space) or the session
  scratchpad. Debate files are never committed; only the consolidated decision
  enters `docs/`.

## Procedure

1. **Confirm debate is the right tool.** One concrete proposal → use
   `orgmemory-architecture-challenge` instead. Two live alternatives → debate.
2. **Write the debate brief to a file — never paste it inline.** The standing
   correction is explicit: create a prompt file and tell the counterpart to
   read it. The brief contains: the question, both candidate architectures,
   file paths for the evidence each side would use, hard constraints
   (read-only, no edits, no mutations), and the required output shape
   (position, attacks on the other side, concessions).
3. **Dispatch the two debaters.** Each gets a role — defender of A, defender
   of B (or defender vs skeptic) — and reads the brief file. Drive the
   counterpart through orca: send the "read `<file>` and respond into
   `<file>`" instruction, then `orca terminal wait --for tui-idle` with a
   timeout before reading. Each side may inspect the repo; responses are
   appended to a debate-record file, not relayed by paraphrase.
4. **Run bounded rounds (2–3).** Each round, every side must attack the
   other's position with concrete repo evidence, not restate its own. Stop
   when attacks repeat or both sides concede the same points.
5. **Judge from the record only.** A fresh session gets the debate record and
   these constraints verbatim: do not inspect files, do not call tools, do not
   edit anything; answer from the debate record; produce a one-sentence final
   architecture decision plus rationale; a tie is not an allowed outcome.
6. **Record the outcome.** Write the verdict to a file (e.g.
   `tmp/<topic>-verdict.md`), confirm it exists before reporting it, then
   consolidate the decision — including the losing position as the rejected
   alternative — into the active design or `docs/decisions` per CLAUDE.md.

## Verification

Done when the debate-record and verdict files exist on disk, the verdict
contains a single committed decision (no hedge, no tie), and the decision plus
rejected alternative are consolidated into the increment design or a decision
entry.

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
- **Blank or truncated output:** ask that side to repeat its complete response
  as plain Markdown, no tools, no formatting extras (this recovery has been
  needed and works).
- **Premature agreement:** if both sides agree in round 1 without evidence,
  inject concrete suspected contradictions and force one more round before
  going to the judge.
- **Judge refuses to commit:** re-ask with the one-sentence-decision
  constraint restated; if it still hedges, surface both options and the
  record to the project owner for the final call.
