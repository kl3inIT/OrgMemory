# Direct Skill sharing plan

## 0. Architecture challenge

- [x] Dispatch a fresh read-only reviewer against `challenge-brief.md`.
- [x] Record the verdict, strongest contradiction, final decision, and rejected
      alternative in `challenge-verdict.md` and this design.

## 1. Domain and API

- [ ] Add a Skill-only direct-publication command authorized by the challenged
      permission rule.
- [ ] Create the immutable Revision and Release atomically without a review
      case while preserving exact package references and audit evidence.
- [ ] Expose an explicit REST command and generated governance affordance.
- [ ] Keep the reviewed lifecycle unchanged for every non-Skill profile.

## 2. Product flow

- [ ] Replace the Skill Draft submit-for-review card with one explicit version
      and `Publish Skill` action.
- [ ] Keep historical review evidence readable without making it the default
      Skill journey.
- [ ] Preserve the existing Asset theme, shared page system, and generated API
      client.

## 3. Verification

- [ ] Add focused core/API tests for authorization, atomic release creation,
      package pinning, conflicts, and non-Skill rejection.
- [ ] Update frontend unit and browser tests for the direct Skill journey.
- [ ] Run backend static analysis or its documented fallback, focused Gradle
      tests, terminating `clean test`, frontend lint/typecheck/tests/build, and
      a real browser flow.

## 4. Consolidation and delivery

- [ ] Reconcile the Asset Registry spec and mirrored test matrix.
- [ ] Update Architecture and roadmap facts without duplicating the design.
- [ ] Move this increment to completed with verification evidence.
- [ ] Merge `origin/main`, rerun affected gates, open one PR, monitor CI and
      CodeRabbit, merge remotely, and verify the deployed behavior.
