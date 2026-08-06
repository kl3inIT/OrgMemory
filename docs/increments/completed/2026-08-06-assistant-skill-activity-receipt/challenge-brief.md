# Independent Challenge: Assistant Skill Activity Receipt

You are an adversarial, read-only architecture reviewer. Attack the proposal;
do not validate it by default. Read `CLAUDE.md`, `docs/conventions.md`,
`docs/guidelines/agent-safety.md`, the Assistant spec/test pair, relevant
decision filenames, and the implementation paths named below. Verify claims in
code. Do not edit files or mutate runtime state.

OrgMemory is a governed organizational memory layer. Its Assistant may use only
actor-authorized evidence and a fixed, read-only Skill tool loop. The product
promise at stake is that useful agent progress remains visible without turning
progress UI into a metadata, prompt, resource, or reasoning disclosure channel.

## Rule under review

> After successful actor-authorized exact-release activation, a transient
> activity event may contain the bounded Skill title. The browser may retain a
> sanitized current-turn receipt (`Using <title> skill` plus closed phase
> states) until the next submit or context change, but the server does not
> persist it. The waiting row ends only when visible Assistant output exists,
> not when transport status settles.

Current rules and enforcement:

- `docs/specs/domains/assistant-and-mcp.md` says activity currently carries no
  Skill identity, arbitrary prose, or history and clears at first token.
- `core/.../AssistantAgentActivity.java` carries only phase/state/count.
- `AssistantSkillToolCallbacks.java` emits activity around actor-scoped Skill
  operations.
- `UiMessageStream.java` marks activity parts transient.
- `assistant-page.tsx` clears activity in `onFinish` and renders its waiting row
  only while AI SDK status is busy and no visible output exists.

## Comparable-system evidence

Pinned Onyx commit: `618b5031bf21463f44e3bed9eb9d5073b806fec0`.

| System | Behavior | Source |
| --- | --- | --- |
| Onyx Craft | `Using <skill> skill` is a first-class tool card | `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolCard.tsx` |
| Onyx Craft | Skill-related calls get active grouping and auto-collapse after answer text | `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolGroup.tsx`, `BuildMessageList.tsx` |
| Onyx Craft | Safe display state contains `skillName` separately from raw tool output | `tmp/onyx/web/src/app/craft/types/displayTypes.ts` |
| OrgMemory | Activity is transient and browser copy is closed; Skill payload is never persisted | implementation paths above |

## Operational motivation

In production the activity indicator can disappear roughly one to two seconds
before answer text paints. A completed Skill-backed response then shows no
visible evidence that discovery, activation, or resource read occurred. Users
cannot distinguish a Skill-backed turn from ordinary retrieval after the answer
arrives.

## Required verdict

Return:

1. explicit `ACCEPT`, `REVISE`, or `REJECT`;
2. strongest concrete attack against the proposal;
3. must-fix items with repository evidence;
4. whether successful authorized Skill title disclosure is safe;
5. whether current-turn-only receipt continuity is truthful enough;
6. a recommended closed activity/receipt state machine and failure handling;
7. the rejected alternative.

## Reviewer availability record

Two initial Fable 5 invocations failed before returning a verdict: the first
terminal disconnected and rejected recovery with `terminal_not_writable`; the
second wrapper exited with `Execution error`. A third Fable 5 session completed
successfully with a read-only `Read,Grep,Glob` toolset. No fallback reviewer was
used for the recorded verdict.
