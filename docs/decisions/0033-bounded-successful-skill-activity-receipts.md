# 0033 — Bound successful Skill identity in transient Assistant receipts

Status: accepted
Date: 2026-08-06

## Context

Assistant Skill activity was deliberately transient and identity-free. That
kept the progress channel closed, but it also made a completed Skill-backed turn
indistinguishable from ordinary retrieval and allowed the waiting row to vanish
before visible answer output painted. Onyx Craft demonstrates that named Skill
tool cards can remain truthful and compact, but OrgMemory Skill titles are
direct-published content and cannot enter server-attributed UI without an
additional boundary.

## Decision

After an exact-release activation succeeds through the current actor's live
Skill authorization path, the transient activity stream may carry a sanitized
display title and a positive turn-local activation ordinal. The activity record
itself trims the title, removes control characters, and caps it at 80
characters. Discovery and failed/denied activation remain identity-free.
Resource activity may carry only the ordinal of an exact release successfully
activated earlier in the same turn.

The browser may retain the resulting plain-text receipt for the current turn
and auto-collapse it after visible answer output. It does not persist or
reconstruct receipts from transcript history. A per-turn visible-output latch,
not transport completion alone, owns the wait-to-answer handoff and has explicit
rendered-answer, error, abort, stop, context-change, and empty-finish terminal
states. A source frame without rendered answer text does not end the latch.

## Consequences

- Users can see which governed Skill was actually activated without seeing
  tool payloads or denied catalog metadata.
- Multiple Skill activations remain separately attributable.
- Reloaded history intentionally contains no Skill receipt until a separate
  retention/replay policy is designed.
- The receipt is evidence of tool use only; it grants no authority and makes no
  correctness claim.

## Rejected alternatives

- A generic unnamed receipt was rejected because it does not solve the
  observability problem while retaining most UI complexity.
- Durable receipt persistence was rejected because it requires explicit
  retention, revocation, replay, and schema semantics.
