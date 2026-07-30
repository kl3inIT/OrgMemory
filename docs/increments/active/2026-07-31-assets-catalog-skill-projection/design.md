# Assets Catalog Type Projection

Status: implementation active.

## Problem

The Assets catalog already lists the latest exact release the current actor may
use, but its type boundary is hidden inside a conventional select. That makes
the shared registry difficult to understand and makes Skills look either
missing or like they require a separate marketplace.

The product needs to make the four governed Asset profiles legible without
inventing a second Skill lifecycle, fake marketplace activity, or catalog
facets the server cannot authorize and return.

## Outcome

- Keep one **Assets** catalog and expose **All assets**, **Prompt templates**,
  **Work instructions**, **Capability packs**, and **Skills** as a prominent
  controlled type projection.
- Preserve server-backed search, type filtering, sorting, pagination, and exact
  release navigation in URL state.
- Preserve the existing list and grid representations and their responsive,
  keyboard-accessible controls, with the visual catalog grid as the clean-URL
  default.
- Use only fields returned by the authorized catalog contract.

## Constraints

- The catalog continues to show only the latest non-withdrawn exact release the
  current actor may use.
- Type selection remains the existing `type` query parameter; the client does
  not load an unbounded collection and filter it locally.
- No type counts are shown because the server returns only the authorized total
  for the selected page query, not authorized facet counts.
- No Skill category, rating, use count, contributor score, or contribution CTA
  is shown until its governed product contract and real route exist.
- The existing product shell, tokens, shadcn/Radix primitives, light/dark
  themes, and page layout remain authoritative.
- The image prototype informs layout and hierarchy only; it does not introduce
  a new brand palette or visual theme into the product application.

## Architecture Challenge

The proposal was reviewed independently with Fable 5 before implementation.
The strongest counterargument was that Skills have installation, validation,
and contribution workflows distinct enough to justify a separate catalog and
lifecycle.

Repository evidence instead shows that `SKILL` is already one enabled
`AssetType`, uses the same stable identity, immutable revision/release,
authorization, availability, and governance kernel, and is delivered from the
same `/api/assets/catalog` contract. The final choice is therefore one Asset
catalog with a contextual Skills projection. The rejected alternative is a
second Skill marketplace or lifecycle, which would duplicate governance and
make cross-type discovery inconsistent.

The lifecycle review also found that inspection, validation, evaluation, and
review are evidence or governance activities rather than additional public
catalog states. This increment does not change lifecycle semantics.

## Deferred

- Authorized facet counts and server-side Skill category filtering.
- Contribution incentives, usefulness measurement, recognition, and adoption
  analytics.
- Browser-native Skill authoring; the current supported contribution path
  starts in the CLI and hands the Draft to Governance.
- Asset detail, installation, and Governance workspace redesigns.
