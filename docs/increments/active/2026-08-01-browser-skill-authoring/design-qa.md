# Browser Skill authoring design QA

## PR 1 — creation entry and ZIP upload

Status: local browser pass; final increment comparison remains open.

Reference: the owner-supplied Onyx Create Skill menu at 1598 x 910.
Implementation capture:
`apps/output/design-qa/skill-create-entry.png` at 1598 x 910 (local verification
artifact, intentionally not committed).

The implementation preserves the reference's three task choices and compact
decision language while adapting them to the existing OrgMemory sidebar,
PageLayout, tokens, cards, and governed Draft boundary. It avoids a standalone
Skill catalog and keeps the upload method as the only interactive card until
the remaining methods ship. Desktop and 390 x 844 mobile browser assertions
passed without horizontal overflow.

Final PASS requires scratch and GitHub states, error/partial-success states,
and the complete same-viewport comparison in PR 3.

## PR 2 — scratch and mutable Draft package

Status: local browser pass; final increment comparison remains open.

Implementation captures (local verification artifacts, intentionally not
committed):

- `apps/output/design-qa/skill-scratch-authoring.png`, light theme at
  1536 x 1024.
- `apps/output/design-qa/skill-draft-replacement.png`, dark theme at
  1536 x 1024.

The Scratch surface uses the existing OrgMemory page shell, form, Card,
validation, and semantic color tokens. Package metadata stays beside the
authoring form on desktop instead of becoming a second workflow. The Draft
replacement surface reuses the same package input and inspection language so
the user edits the existing Asset rather than entering a duplicate Skill
workspace. Focused Playwright coverage passed 7/7, including Scratch preview
invalidation, Upload errors, optimistic replacement, authorization-driven
actions, and mobile catalog overflow.

The reference flow remains the owner-supplied Onyx creation menu and accepted
OrgMemory Asset prototype. PR 2 intentionally preserves OrgMemory visual
identity rather than copying Onyx styling. Final same-viewport PASS remains a
PR 3 closeout gate after the GitHub, empty, error, and partial-success states
exist.
