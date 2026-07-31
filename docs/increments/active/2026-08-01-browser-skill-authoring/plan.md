# Browser Skill authoring plan

## 0. Architecture and reference gates

- [x] Verify the current OrgMemory Asset, Skill package, Draft, publication,
      authorization, storage, and generated-client paths.
- [x] Study the pinned and current Onyx Skill creation source.
- [x] Obtain an independent Fable 5 challenge of Draft replacement and private
      GitHub credential boundaries.
- [x] Record `challenge-verdict.md` and fold every must-fix constraint into the
      implementation plan.

## 1. PR 1 — creation navigation

- [x] Replace the Assets header link with an accessible `Add asset` category
      dropdown.
- [x] Route Skill to `/assets/new/skill` and leave unsupported categories
      visibly disabled.
- [x] Add the creation-only Skill hub with Start from scratch, Upload, and
      Import from GitHub actions using current OrgMemory theme primitives.
- [x] Remove the dead full-page type chooser.
- [x] Ship the existing canonical ZIP upload path so this slice creates a real
      Draft instead of replacing one dead end with another.
- [x] Add focused unit/browser coverage and pass frontend completion gates.
- [ ] Complete the PR/CI/CodeRabbit/merge/deploy/live-verification loop.

## 2. PR 2 — scratch, upload, and mutable Draft

- [ ] Add bounded server inspection for `SKILL.md`, ZIP, and browser-packaged
      folders without storing or executing content.
- [ ] Add scratch authoring for Details, Instructions, Supporting files,
      Knowledge Space, and classification.
- [ ] Create a governed private Draft from inspected or scratch content.
- [ ] Add optimistic, reference-safe package replacement for mutable Skill
      Drafts and retain exact immutable Revision/Release bytes.
- [ ] Add a migration that permits only deletion of `DRAFT` payload references;
      keep all updates and Revision/Release deletion rejected and tested.
- [ ] Run replacement under the Asset lock plus expected Draft version; write a
      durable supersession row in the swap transaction and retry exact-reference
      cleanup after commit.
- [ ] Keep inspection stateless and re-run the canonical package validator at
      both create and replace time.
- [ ] Update generated OpenAPI clients, focused backend/frontend tests, domain
      spec, and mirrored test matrix.
- [ ] Complete the PR/CI/CodeRabbit/merge/deploy/live-verification loop.

## 3. PR 3 — GitHub import and product completion

- [ ] Add bounded public GitHub preview and optional private preview through an
      administrator-managed GitHub App source connection.
- [ ] Resolve and persist immutable commit provenance, preview multiple Skills,
      and import a selected subset with per-item results.
- [ ] Enforce HTTPS host allowlisting, redirect and Authorization stripping,
      administrator opt-in for private GitHub App import, credential-use audit,
      40-character commit pinning, and bounded one-pass archive inspection.
- [ ] Import each selected Skill in its own transaction and return independent
      results without an outer batch transaction.
- [ ] Finish error, empty, partial-success, keyboard, theme, responsive, and
      authorization states.
- [ ] Update public Product Guide content only where the shipped behavior needs
      explanation; do not publish internal design details.
- [ ] Run full backend/frontend/docs/release gates and same-viewport visual QA;
      record the final result in `design-qa.md`.
- [ ] Complete the PR/CI/CodeRabbit/merge/deploy/live-verification loop.

## 4. Closeout

- [ ] Reconcile `ARCHITECTURE.md`, the Asset Registry spec/test pair, and
      roadmap against the merged implementation.
- [ ] Archive this increment only after production verifies all three creation
      paths and ordinary Draft publication remains intact.
- [ ] Persist the verified architecture and delivery checkpoint in Northstar.
