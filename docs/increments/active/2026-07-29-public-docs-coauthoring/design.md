# Public Documentation Co-Authoring Design

## Problem

The first public corpus proved the Fumadocs application, publication boundary,
search, API generation, bilingual shell, and independent production delivery.
Its information architecture was intentionally modeled on the much larger Onyx
documentation set. At OrgMemory's current scale, that model creates sparse root
categories and mixes different reader needs:

- Start Here combines orientation, a tutorial, concepts, and a domain lifecycle;
- Build & Integrate combines task guidance with generated reference;
- System Design combines architecture explanations with thesis evaluation and
  requirement traceability;
- Deployment and administration are promoted to roots with only one or two
  authored pages.

The project owner also needs a deliberate learning loop: authoring each page
must improve their own architectural understanding instead of producing a
large corpus for passive review.

## Goals

- Establish one durable target information architecture for the public product
  documentation.
- Review and publish one document at a time in English and Vietnamese.
- Make the project owner the content owner and architectural learner for every
  authored page.
- Keep public product documentation separate from private SRS, SDD, ADR,
  increment, test, runbook, and thesis evidence.
- Preserve stable external URLs with redirects when taxonomy changes move a
  page.
- Keep every published claim traceable to current repository and runtime
  evidence.
- Keep the owner review loop local-first: ordinary page review starts only the
  independent docs application and uses hot reload.

## Non-Goals

- Bulk rewriting or machine-translating the existing corpus.
- Publishing raw internal repository documentation.
- Turning the public site into an SRS/SDD or university report.
- Creating empty placeholder pages to make a category appear mature.
- Maintaining two competing current-state explanations.

## Audience And Ownership

The project owner is the first reader and final editorial owner. Secondary
readers are product users, administrators, self-hosted operators, integrators,
security reviewers, and evaluators. Each public page identifies one primary
reader need and one dominant documentation form.

The repository remains the engineering source of truth. Public MDX is a
reviewed, audience-oriented projection. Northstar records continuity and next
steps but does not replace repository or runtime evidence.

## Information-Architecture Decision

Use four documentation categories adapted from the Diátaxis reader needs:

1. **Getting Started** — orientation and the first successful tutorial.
2. **Guides** — task-oriented product, administration, deployment, operations,
   and integration procedures.
3. **Architecture & Security** — explanations of system structure, domain
   models, data flows, trust boundaries, and design rationale.
4. **Reference** — exact API, configuration, connector, permission, MCP, error,
   and compatibility contracts.

Deployment & Operations remains a Guides subgroup until it has enough reviewed
material to justify an independent documentation category. Changelog remains a
global navigation link and also uses a Fumadocs root context containing Latest,
recent versions, and the internal archive. That presentation root keeps the
area selector visible and gives release history a focused tree; it is not a
fifth Diátaxis documentation category.

### Strongest Counterargument

Audience roots such as Deployment, Admins, Developers, and Security provide
direct entry points and match mature enterprise documentation such as Onyx.
Keeping the first-release structure also avoids URL movement.

### Repository Evidence And Final Choice

The current manifest has 17 authored pages and seven generated API pages.
Deployment has one authored page; administration has two; the developer root
contains one guide plus generated reference. The sparse roots do not yet earn
separate navigation modes. Fumadocs supports root folders as a presentation
mechanism, so the product should choose roots from reader needs rather than
copying another product's scale.

Choose the four-root structure now. Preserve old URLs with permanent redirects.
Promote a subgroup only after real reviewed pages and reader demand justify it.

The rejected documentation alternative is retaining the five audience-based
roots and documenting their overlap. That would describe the inconsistency
rather than remove it. For Changelog specifically, the rejected alternative is
a custom runtime sidebar or a GitHub-hosted archive: the former duplicates
Fumadocs navigation behavior, while the latter makes public history depend on
repository visibility. The independent evidence and must-fix constraints are
recorded in [challenge-verdict.md](challenge-verdict.md).

## Public And Private Documentation Boundary

Public product documentation lives only under `apps/docs/content/docs`.

Private engineering and thesis evidence remains in its canonical repository
homes:

- `docs/specs/domains` — living requirements and domain contracts;
- `docs/decisions` — architectural decisions and rationale;
- `docs/increments` — point-in-time designs and execution history;
- `docs/tests/domains` — mirrored verification coverage and gaps;
- `docs/runbooks` — internal operational procedures;
- university deliverables — thin views over those canonical sources, not a
  second product documentation tree.

Functional coverage, requirement traceability, and academic future-work pages
must not remain mixed into Architecture & Security. A later content increment
will either retire them from public navigation or rewrite a public-safe subset
as capability status or known limitations.

## Co-Authoring Loop

One content increment owns one conceptual page and its adjacent reviewed
Vietnamese translation:

1. **Evidence packet** — inspect current code, schema, contracts, specs, tests,
   decisions, and runtime evidence.
2. **Owner context** — ask focused questions until the owner can state the
   purpose, boundary, invariants, and important tradeoffs in their own words.
3. **Outline** — agree on the reader question and section structure before
   drafting prose.
4. **English draft** — write the canonical public explanation with
   `sourceRefs`.
5. **Teach-back checkpoint** — the owner reviews and explains the page back;
   unresolved confusion returns to evidence or outline.
6. **Reader test** — test realistic reader questions, ambiguity, assumed
   context, links, accessibility, and publication safety.
7. **Vietnamese draft** — translate the approved meaning, not merely the
   English sentence structure.
8. **Local review** — inspect both locales through the independent local docs
   app without starting product services.
9. **Delivery loop** — run docs gates, PR review, merge, immutable build,
   deploy, and live verification.
10. **Continuity** — update the page register and consolidated Northstar
   checkpoint.

Structural increments may change taxonomy or redirects across pages, but they
must not silently rewrite page content. The delivery loop may run
autonomously; content work pauses at the owner context, outline, and teach-back
checkpoints.

## Definition Of Done For One Page

- one primary audience and reader question are explicit;
- the owner can explain the page's model and boundaries without reading it;
- every current-behavior claim has current source evidence;
- planned behavior is labeled or excluded;
- English is approved before Vietnamese is authored;
- diagrams exist only when they materially clarify a relationship or flow and
  include adjacent explanation and useful alternative text;
- no internal path, private host, secret, customer data, raw runbook, or
  unapproved implementation detail is published;
- navigation, links, search, Markdown output, accessibility, and mobile layout
  pass;
- the exact merged revision is deployed and verified before the page register
  advances.

## Delivery Sequence

1. Record this program and close the completed portal-delivery increment.
2. Migrate only the taxonomy, page locations, metadata, manifest, colors, and
   redirects; do not rewrite prose.
3. Co-author pages in the queue maintained by `apps/docs/AUTHORING.md`.
4. Re-evaluate category promotion only after reviewed content and reader
   evidence change the scale.
