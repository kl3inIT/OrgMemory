---
name: orgmemory-session-handoff
description: Keep OrgMemory work alive across context boundaries — checkpoint state into the active increment before /compact or session end, run the takeover preflight when inheriting the repo, and hand work to another agent via a handoff file instead of an inline prompt. Use before compaction, at takeover, at handoff, or when ending a session mid-increment. Do not use for end-of-increment consolidation (CLAUDE.md Increment Workflow owns that) or for the user's personal continuity (Northstar owns that).
---

# OrgMemory Session Handoff

Context windows end; increments don't. The repository is the system of record,
so any state that exists only in the conversation is one compaction away from
being lost. This skill covers the three boundaries where that loss actually
happened or was prevented: compaction, takeover, and agent-to-agent handoff.

## Purpose

Make any OrgMemory session resumable: a fresh agent reading the repo plus one
checkpoint or handoff file can continue the work without re-asking the user
for decisions that were already made.

## Trigger Conditions

- Compaction is imminent (the user says "để tôi compact", or context is
  clearly near its limit while an increment is mid-flight).
- You are taking over the repo ("tiếp quản", a new session inheriting active
  work, or resuming after a long gap).
- Work is being handed to another agent or model (orca terminal, review
  dispatch, debate counterpart).
- The session is ending with an increment still active.

## Required Context

- The active increment directory (`docs/increments/active/<slug>/`) — its
  `plan.md` is the authoritative home for work-in-progress state.
- `tmp/` or the session scratchpad for handoff files (never committed).
- For takeover: nothing is assumed — the preflight itself establishes context.

## Procedure

### Checkpoint (before /compact or session end)

1. Write execution state into the active increment's `plan.md` (or a
   dedicated notes file beside it), not into the chat: which phases/PRs are
   done with their verification evidence, what is in flight, which gates must
   be re-run, which decisions were made this session (and which the user has
   NOT yet made), and the exact next action.
2. Commit rule unchanged: docs updates ride with their increment per repo
   conventions; the checkpoint may stay uncommitted in the worktree, but it
   must be on disk before compaction.
3. Record the checkout mode and target branch. In a single active coding
   session, resume the feature branch in the current checkout; use a fresh
   worktree only when concurrent sessions need isolation.

### Takeover preflight (inheriting the repo)

Run before touching the assigned problem, in this order:

1. `git status --porcelain` — inventory every dirty path. Stop for unknown or
   overlapping changes; explicitly identified unrelated files may remain only
   if they are preserved and excluded from all staging/reset/cleanup commands.
2. `git fetch origin` and compare ahead/behind against `origin/main`; note the
   current branch and whether this is single-session current-checkout mode or a
   concurrent-session worktree.
3. Read `AGENTS.md`, `CLAUDE.md`, and the `ARCHITECTURE.md` sections relevant
   to the assignment.
4. List `docs/runbooks/` and `docs/increments/active/` — active increments
   tell you what is mid-flight and where its checkpoint lives.
5. Only then start on the assigned problem, resuming from the checkpoint if
   one exists.

### Handoff to another agent

1. Write the handoff as a file (in `tmp/` or the scratchpad), never as an
   inline prompt — the standing correction is explicit. Include: objective,
   hard constraints (read-only? no mutations? which branch?), exact file
   paths to read, what is out of scope, and the expected output format and
   destination file.
2. Point the receiving agent at the file ("Read `<path>` and perform it");
   over orca, send the instruction and wait for idle before reading back.
3. Verify the counterpart's output file exists on disk before reporting the
   handoff complete.

## Verification

A checkpoint is good when a fresh session could continue using only the repo
plus the checkpoint — test it by rereading it as a stranger. A takeover is
good when preflight findings (branch, ahead/behind, active increments) were
stated before any change. A handoff is good when both the handoff file and
the counterpart's output file exist on disk.

## Failure Handling

- **Context was lost before a checkpoint was written:** reconstruct from
  evidence — `git log`, open PRs, the active increment docs — and state what
  was reconstructed vs. what remains unknown. Do not guess at undocumented
  decisions; re-ask the user only for those.
- **Dirty tree at takeover:** surface exactly what is dirty. Stop for unknown
  or overlapping paths; preserve user-identified unrelated paths without
  staging, moving, resetting, or deleting them.
- **Handoff counterpart unavailable or produces nothing:** the handoff file
  makes retries cheap — respawn and re-point; if it stays unavailable,
  record that and continue solo or escalate to the user.
- **Checkpoint and repo disagree after resume:** current repo and runtime
  evidence win; update the checkpoint, then proceed.
