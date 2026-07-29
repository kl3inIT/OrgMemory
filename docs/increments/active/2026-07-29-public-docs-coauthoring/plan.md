# Public Documentation Co-Authoring Plan

Execute the accepted [co-authoring design](design.md) through small,
reviewable increments. One content PR owns one conceptual page and its reviewed
English/Vietnamese pair. Taxonomy-only work is a separate structural PR.

## Program Foundation

- [x] Record the target information architecture and strongest counterargument.
- [x] Record the page-by-page co-authoring and teach-back workflow.
- [x] Create the durable page register in `apps/docs/AUTHORING.md`.
- [x] Transfer unfinished content follow-ups from the completed portal
  increment.
- [x] Merge the program foundation and taxonomy migration.

## Taxonomy Migration

- [x] Replace the five first-release roots with Getting Started, Guides,
  Architecture & Security, and Reference.
- [x] Group product, administration, deployment/operations, and integration
  procedures under Guides.
- [x] Move generated and authored API contracts under Reference.
- [x] Preserve every moved public URL with an explicit permanent redirect.
- [x] Keep evaluation/traceability content unchanged until its dedicated
  retirement or rewrite increment.
- [x] Update EN/VI metadata, category identity mapping, manifest areas, sitemap,
  tests, and internal architecture guidance.
- [x] Verify old and new URLs, desktop/mobile navigation, category colors,
  accessibility, search, Markdown siblings, and public-output safety.

## Page-By-Page Content

For each queue item in `apps/docs/AUTHORING.md`:

- [ ] assemble the evidence packet;
- [ ] complete owner context questions;
- [ ] approve the outline;
- [ ] review the English draft;
- [ ] complete the teach-back checkpoint;
- [ ] pass reader testing;
- [ ] approve the Vietnamese draft;
- [ ] pass local and CI gates;
- [ ] merge, deploy, live-verify, and advance only that queue item.

Repeat this checklist in the page's PR description rather than duplicating a
new increment directory for trivial prose edits. Create a dedicated design only
when the page introduces a material publication, architecture, or security
decision.

## Exit Gate

The program remains active while priority pages remain in the queue. It may
close when:

- the four-root information architecture is stable;
- every priority page is reviewed in both English and Vietnamese;
- the project owner can explain the end-to-end architecture and its principal
  trust boundaries from the published corpus;
- remaining pages are ordinary maintenance rather than a structured learning
  program.
