# Assistant Answer Behavior Plan

## Goal

Ship permission-safe no-answer wording, adjacent-information labeling, exact
inline citation instructions, and the static Assistant disclosure without
changing retrieval or authorization. One pull request carries the increment;
the agent stops after its CI is green and does not merge it.

## Preconditions And Invariants

- [x] Read the task brief, repository guidance, Assistant spec/test matrix,
  current prompt/service paths, and pinned Onyx/LightRAG sources.
- [x] Recheck Spring AI 2.0 RAG guidance through current official docs.
- [x] Confirm the worktree starts clean on `feat/assistant-answer-behavior`.
- [ ] Keep `AuthorizedEvidenceScope`, retrieval, authorization, ranking,
  scorers, and official fixtures unchanged.
- [ ] Keep evidence/user-context injection safety and personalization-only
  semantics intact.

## 1. Frame The Increment

- [x] Author `design.md` with the measured failures, research citations,
  R1-R6, both answer paths, browser disclosure, and security boundaries.
- [x] Author this executable plan before source implementation.
- [x] Mark the increment active in `docs/roadmap.md` with the production sweep
  as its remaining gate.
- [ ] Commit the increment framing before implementation.

## 2. Model-Backed Answer Behavior

- [ ] Expand `AssistantPromptFactory.SYSTEM_INSTRUCTION` to require:
  same-language user-perspective uncertainty, one escalation line,
  adjacent-information labeling, all-and-only inline `[n]` citations, and no
  restricted-resource speculation or pipeline meta voice.
- [ ] Apply the shared instruction to both the canonical rendered prompt and
  the already-verified LightRAG generation request.
- [ ] Preserve the untrusted-evidence, untrusted-user-context, and
  personalization-only authorization lines.
- [ ] Add focused tests for the positive R2/R3/R5 invariants and a lower-cased
  banned-phrase list, including both prompt construction paths.

## 3. Empty-Evidence Answer

- [ ] Replace the fixed English fallback with deterministic Vietnamese and
  English user-perspective variants selected from the question language.
- [ ] Keep the path model-free, citation-free, concise, and limited to the
  not-found statement plus one escalation sentence.
- [ ] Extend `AssistantServiceTests` to cover Vietnamese and English wording,
  no banned pipeline voice, no model call, and no citations.

## 4. Browser Disclosure

- [ ] Render `Câu trả lời chỉ dựa trên tài liệu bạn có quyền truy cập.` below
  every Assistant answer, including replay and empty-evidence output.
- [ ] Do not render the disclosure below user messages.
- [ ] Follow the existing colocated static-copy convention; do not introduce a
  one-string localization subsystem.
- [ ] Add focused component coverage for both roles.
- [ ] Commit the browser change separately inside the same branch/PR.

## 5. Verification

- [ ] Pin Node 24 before every Node/pnpm command.
- [ ] Run focused backend tests while iterating.
- [ ] Run `./gradlew.bat --no-daemon compileJava` and `:core:test`.
- [ ] Run `./gradlew.bat --no-daemon clean test` and capture its direct exit
  code without piping.
- [ ] Run web `lint`, `typecheck`, `test:unit`, and production `build` under
  Node 24.
- [ ] Run the repository mechanical Java floor when JetBrains inspection is
  unavailable.
- [ ] Review `git diff` and `git status`; prove no retrieval, authorization,
  scorer, or official-fixture file changed.
- [ ] Keep the worktree clean after commits.

## 6. Pull Request

- [ ] Fetch and merge `origin/main` immediately before `gh pr create`, resolve
  only in-scope conflicts, and rerun affected gates if the merge changes the
  tested tree.
- [ ] Create one PR titled
  `feat(assistant): permission-safe no-answer behavior and citation discipline`.
- [ ] In the PR body include the real P027/P032 current excerpts and target
  forms, research links, exact local gates, scope exclusions, and the
  post-merge sweep below.
- [ ] Wait for CI to finish green and address in-scope failures or actionable
  review feedback.
- [ ] Do not merge the PR. Report the URL, head SHA, commits, checks, and
  remaining production gate.

## 7. Owner-Run Post-Merge Verification

This step is intentionally outside the PR and is not run by the agent:

- [ ] After merge and deployment, run the official 50-case production sweep.
- [ ] Preserve permission deny 7/7.
- [ ] Keep citation at least 41/43; target 43/43.
- [ ] Verify P027 and P032 use the user's language, contain no pipeline voice,
  and end with one escalation line.
- [ ] Verify P035 labels neighboring authorized content as the nearest
  available information rather than the direct answer.
- [ ] Verify P031 cites DOC001+DOC011 and P001 cites DOC001 only.
- [ ] Only after that evidence, reconcile the Assistant spec/test matrix, mark
  this increment shipped in the roadmap, write verification evidence, and move
  the increment to `completed/`.
