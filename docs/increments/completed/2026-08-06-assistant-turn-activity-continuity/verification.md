# Assistant Turn Activity Continuity Verification

Date: 2026-08-06

## Delivered behavior

- Thinking and current-turn Skill receipts share one activity surface anchored
  after the initiating user message. Appending an Assistant placeholder or
  answer no longer relocates that surface.
- Whitespace, zero-width characters, and Markdown framing alone do not count as
  visible answer output, so they cannot end the waiting state before Streamdown
  has meaningful content to paint.
- Activation-only Skill receipts are non-interactive and do not expose an empty
  detail panel. Resource progress stays expandable, settles closed after visible
  answer text, and a failed resource remains open unless the reader toggled it.
- Activity remains a closed UI projection. No instruction content, tool
  input/output, identifier, or raw failure was added to the browser contract.

## Reference evidence

The interaction lifecycle was ported from Onyx commit
`618b5031bf21463f44e3bed9eb9d5073b806fec0`:

- `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolCard.tsx`
- `tmp/onyx/web/src/app/craft/components/tool-cards/CraftToolGroup.tsx`
- `tmp/onyx/web/src/app/app/message/messageComponents/timeline/hooks/useTimelineExpansion.ts`

OrgMemory retains its own sanitized receipt and shadcn `Collapsible`; it does
not copy Onyx packet payloads or adopt AI Elements `Tool` semantics.

## Gates

All Node commands used Node `24.15.0` and the frozen lockfile.

- `corepack pnpm install --frozen-lockfile`: passed.
- `corepack pnpm --filter @orgmemory/web gen:api`: passed; generated client is
  ignored and was used only to satisfy the clean-worktree typecheck.
- Focused Vitest for Assistant activity: 3 files, 10 tests passed.
- Full web Vitest: 29 files, 92 tests passed.
- Web Oxlint: passed.
- TypeScript project build: passed.
- Vite production build: passed; existing large-chunk warnings remain.
- Focused Chromium behavior run for ordering, activation-only receipt, terminal
  waiting, and structured streaming: 4 tests passed.
- Full Chromium Assistant pipeline: 19 functional tests passed; the independent
  high-frequency timing assertion failed as described below.
- `git diff --check`: passed.

No backend Java, schema, API, authorization model, or generated contract changed,
so JetBrains inspection and Gradle gates were not applicable.

## Baseline gap

The existing high-frequency browser test exceeded its `<500 ms` maximum
`requestAnimationFrame` gap on both this branch and a detached, clean
`origin/main` worktree at `14171476`. Two clean-baseline repeats measured
`1244.5 ms` and `693.9 ms`; three branch repeats measured between `687.4 ms`
and `1174.8 ms`. The test starts measurement in `addInitScript`, so page startup
is included before the Assistant stream is submitted. This increment does not
weaken the threshold or mix a harness correction into the UI lifecycle change.

## Remaining risk

- Browser tests prove the stable ordering and component identity, but the mock
  route fulfills one complete SSE body rather than pacing the first Markdown
  fragment over a real network interval. The raw-fragment transition is covered
  deterministically at the visibility-predicate boundary.
- The pre-existing performance gate needs a separate harness change that starts
  measurement immediately before the streamed response, then re-establishes a
  meaningful threshold on controlled runners.
