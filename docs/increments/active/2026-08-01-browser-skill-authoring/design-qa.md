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
