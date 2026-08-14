# Prompt Asset Authoring And Exact-Release Use Plan

## Status

Shipped and verified on 2026-08-14. The browser-native Prompt authoring and
exact-release use slice is recorded in the adjacent `verification.md`; strict
Required Knowledge grounding remains deferred.

## 1. Freeze the existing contract

- [x] Reconcile the current Asset lifecycle, Prompt schema/renderer/execution,
  REST operations, browser gaps, domain spec, test matrix, safety guideline, and
  roadmap dependency.
- [x] Record the approved editor/test/Knowledge layout prototype and distinguish
  its future-state Required control from this increment's supported behavior.
- [x] Bound the increment to current authorization, persistence, direct
  publication, immutable release, evaluation, and optional retrieval semantics;
  no architecture challenge is required for this boundary.
- [x] Add focused characterization fixtures for schema-v1 text prompts,
  placeholder extraction, variable validation, evaluation cases, and optional
  Knowledge serialization before changing the page flow.

## 2. Create one private Prompt working copy

- [x] Enable Prompt in the existing Add asset menu and add
  `/assets/new/prompt` through TanStack Router.
- [x] Load live authorized Asset creation targets and keep server-denied or empty
  target states opaque and actionable.
- [x] Build the shared Prompt name and Description identity, followed by the
  text-template editor and Prompt-specific Usage and Output contracts. Keep
  intended users explicitly descriptive rather than authorization-bearing. Use
  generated namespace/slug under Advanced settings; do not expose IDs,
  coordinates, or raw JSON.
- [x] Map the complete schema-v1 form into `CreateAssetRequest.draft` with one
  typed, unit-tested serializer and generated client mutation. Create the Asset
  and populated working copy atomically; do not follow creation with a second
  Draft write.
- [x] On success, navigate to the canonical Governance working copy. On
  rejection, retain all authored input and show the sanitized server reason.

## 3. Reuse the editor in Governance

- [x] Add a reusable Prompt working-copy editor selected by Asset type while
  preserving the existing generic Governance shell and served capabilities.
- [x] Support schema-v1 text template, objective, audience, use/do-not-use
  guidance, output contract, and known limitations; keep compatibility at
  `chat` and both raw-retention policy flags at `false` in this slice.
- [x] Discover `{{lower_snake_case}}` placeholders and author variable type,
  required/default, sensitivity, allowed values, and optional regex without
  treating browser discovery as canonical validation.
- [x] Save through the generated Draft mutation with the latest
  `expectedLockVersion`; preserve local content and offer recovery on conflict
  or permission loss.
- [x] For an existing ordered-message working copy, render messages read-only,
  disable Prompt-payload save with explicit unsupported-mode copy, and prove the
  text editor cannot replace it with an empty payload. Preserve unchanged generic
  publication when the server serves that capability.

## 4. Author and run bounded tests

- [x] Add, reorder, edit, and remove at most ten evaluation cases with name,
  typed variable inputs, expected fragments, and forbidden fragments.
- [x] Validate test inputs against current variable definitions before save and
  cover missing, unknown, invalid, sensitive, and boundary-count cases. State
  that fixtures are persisted in the release, require synthetic data, never
  import real run values, and require acknowledgement for sensitive variables.
- [x] On released detail only, name the visibly selected exact release, invoke
  its evaluation after explicit confirmation, and show aggregate plus case-level
  pass/fail without persisting raw execution values or provider output.
- [x] Do not auto-run tests during save/publish and do not add Draft execution,
  semantic judges, history, schedules, promotion, or comparison UI.

## 5. Add truthful optional Knowledge grounding

- [x] Expose only None and Optional in the active control and explain that
  Optional can run without evidence.
- [x] Map None to an empty `knowledgeRequirements` list and Optional to bounded
  natural-language requirement strings. Do not add a target/Space selector or
  serialize Knowledge IDs; an explicit run query remains the runtime override.
- [x] Preserve canonical retrieval authorization, untrusted-evidence treatment,
  citation hydration/open checks, and opaque denial behavior in released use.
- [x] Add a regression that Required is not available and that no UI copy claims
  an evidence guarantee the backend does not enforce. Also prove the prototype's
  future-state Scope selector is absent.

## 6. Complete direct publication and released use

- [x] Gate Save and Publish using served capabilities and server-valid Draft
  state; never infer owner authority in the browser.
- [x] Publish through the existing direct-publication mutation and navigate to
  the immutable exact release with `DIRECT` provenance; create no review.
- [x] Preserve existing released Prompt render/run behavior, exact release
  selection, explicit provider confirmation, citations, model-route evidence,
  withdrawn-release denial, and sanitized telemetry.
- [x] Verify sharing remains independent of publication and that a newly
  published Prompt does not become company-visible without explicit audience
  intent.

## 7. Browser quality gates

- [x] Cover form-to-payload mapping, placeholder/variable synchronization,
  evaluation editing, Optional grounding, and permission-gated actions with
  focused unit/component tests.
- [x] Add focused optimistic-conflict and capability-revocation component tests.
- [x] Extend the golden browser POC for authenticated catalog -> atomic create ->
  Governance edit -> direct publish -> exact-release evaluate, including the
  persisted-sensitive-fixture warning and no-evidence Optional copy.
- [x] Add browser variants for create rejection with no orphan,
  ordered-message read-only protection, denied creation, and withdrawn release.
- [x] Verify labels, non-color test status, narrow layout, and horizontal overflow.
- [x] Verify keyboard order, error focus, light/dark themes, reduced motion, and
  loading, empty, and error states.
- [x] Compare the implemented surface with `prompt-editor-prototype.png`; use the
  prototype for hierarchy, not pixel-level replacement of shared design tokens.

## 8. Verification and consolidation

- [x] Run Node 24 and the narrow frontend lint/typecheck/unit/browser gates while
  iterating; then run the web production build and all completion gates required
  by the changed files.
- [x] If backend code or OpenAPI changes become necessary, stop and re-scope,
  then add the focused backend/contract/static gates before continuing.
- [x] Reconcile `docs/specs/domains/asset-registry.md` and its mirrored test
  matrix with implementation evidence and refreshed `Source:`/`Reconciled:`
  lines. Update `ARCHITECTURE.md` only if project-wide implemented facts change.
- [x] Capture authenticated browser evidence. Keep local validation, PR CI,
  merge, exact-SHA deployment, runtime verification, and worktree cleanup as
  separate gates.
- [x] Move this increment to completed only after behavior, documentation, and
  required verification all agree; then update the roadmap from active to
  shipped.

## Stop conditions

Stop and obtain a new architecture decision before implementing any of:

- strict Required Knowledge or fail-before-provider semantics;
- a new authorization relation, persistence model, review path, publication
  mode, mutable alias, or personal prompt store;
- Draft-time provider execution, automated promotion, or unbounded evaluation;
  or
- a handwritten transport that bypasses the committed generated contract.

## Completion evidence

The final verification record must name the exact branch and commit, changed
contracts, focused and completion test commands, authenticated browser path,
known gaps, PR/CI result, merged SHA, deployed SHA, runtime proof, and cleanup
state. Passing CI or a healthy deployment alone is not visual verification.

See `verification.md` for the exact reviewed branch and commit, PR/CI and
workflow runs, merged and deployed SHA, browser path, known gaps, and cleanup
state.
