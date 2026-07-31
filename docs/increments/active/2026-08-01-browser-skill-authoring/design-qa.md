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

## PR 3 — GitHub import and completed creation flow

Status: local PASS; production verification remains the delivery gate.

The owner-supplied Onyx Skills reference and the OrgMemory GitHub import were
compared together at 1598 x 910. Onyx contributes the three clear creation
choices and compact task language. OrgMemory keeps its existing sidebar,
PageLayout, form density, semantic tokens, and governed Draft placement rather
than copying the standalone Onyx catalog or its visual theme.

Implementation captures (local verification artifacts):

- `apps/output/design-qa/skill-github-import.png`, dark theme at 1598 x 910.
- `apps/output/design-qa/skill-github-import-mobile.png`, light theme at
  390 x 844.
- `apps/docs/public/images/product-guides/skill-github-import.png`, sanitized
  public-guide capture at 1598 x 910.

The desktop state visibly covers an authorized destination Knowledge Space, a
private administrator-approved connection, the resolved commit, two selectable
valid Skills, and one rejected package. Knowledge Space selection precedes
connection discovery and preview, matching the backend Skill-create permission
boundary without adding a separate workspace.
The mobile state stacks all controls without horizontal overflow. Focused
Playwright coverage verifies exact-SHA preview/import requests, private access,
partial success, Draft links, the authorized connection query, the active GitHub
creation route, and absence of unexpected browser/API errors. Empty and
transport-error states are explicit in the UI, while backend tests cover
anonymous public access, permission-gated preview and connection discovery,
private credential allow/deny audit, rate limits, redirect boundaries, archive
limits, legacy payload compatibility, and independent per-Skill results.

Completion gates passed on Node 24.15.0: full Gradle `clean test`, web lint,
typecheck, unit tests and production build, docs publication checks and build,
OpenAPI drift generation, release policy checks, and focused Chromium flows.
