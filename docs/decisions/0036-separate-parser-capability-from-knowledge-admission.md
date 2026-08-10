# 0036 — Separate parser capability from Knowledge admission

Status: accepted
Date: 2026-08-10

## Context

Knowledge upload originally repeated the same five extensions in Source Ledger,
the worker parser, worker routing, and the browser. The parser implementation
lived inside `apps:worker`, even though governed Assistant evidence needs the
same document-to-canonical-block boundary. Adding an extension to only part of
that chain could admit evidence that failed later or expose parser capability as
product policy.

The existing LightRAG-derived chunkers already understood heading, paragraph,
and table blocks, but the production parser flattened Spring AI/Tika output into
paragraphs. Widening the upload list first would therefore make spreadsheets
searchable as headerless token fragments. CSV also proved materially different:
Tika returned flat text under the measured configurations, while Office, HTML,
and OpenDocument sources retained useful XHTML structure.

## Decision

Document parsing is a reusable adapter in
`integrations:document-parsing-spring-ai`. Its framework-neutral port,
deterministic failure contract, canonical document, and typed block model remain
in `components:graph-rag-core`. The adapter advertises parser capability by
suffix and owns suffix-to-detected-media validation. Knowledge Source Ledger
separately owns which capable formats the product admits, their upload limits,
and their safe delivery disposition. Worker routing consumes the adapter's
capability rather than maintaining another format list.

CSV uses a dedicated BOM-aware, delimiter-sniffing, quote-aware reader. HTML is
sanitized before Tika. Declared archives are not admitted, and zip-based Office
and OpenDocument containers must pass bounded, format-specific structure checks.
Deterministic format and media failures quarantine the revision without retry.

The default processing policy remains the independently challenged
`structured-block-v1`. Its requested snapshot includes the complete per-format
chunk-ceiling map, and its resolved snapshot records the parser, dispatch, and
actual chunker identities. Retries never re-read changed defaults, and existing
READY revisions remain immutable.

## Independent challenge

The active increment's `challenge-brief.md` and `challenge-verdict.md` rejected
whole-document content-type chunker selection. A document may contain headings,
tables, and prose at once; the accepted composite dispatches by canonical block
kind under one named and versioned policy. That verdict also requires requested
and resolved profiles to be pinned before publication and forbids silently
rebuilding existing READY revisions.

## Rejected alternatives

- Keep parsing inside `apps:worker` and later copy it into Assistant upload. This
  duplicates media validation and canonicalization at the exact trust boundary
  that must behave identically for both consumers.
- Put parser dependencies in `core`. This would make the domain depend on Spring
  AI, Tika, POI, and jsoup instead of keeping those replaceable libraries behind
  a neutral port.
- Treat the parser's suffix list as the product allowlist. Parser capability is
  wider and lower-level than admission, size, authorization, retention, and safe
  delivery policy; coupling them would let an adapter upgrade widen the product
  surface accidentally.
- Copy LightRAG's nominal extension table. A supported format must produce
  useful canonical structure and pass a fail-closed media/container contract,
  not merely appear in a suffix list.
- Select one chunker per file type. Mixed documents would force tables and prose
  through one unsuitable algorithm and make retries depend on live routing
  defaults.

## Consequences

- Knowledge and future governed Assistant evidence can reuse one parser without
  sharing their lifecycle or authorization policy.
- The product currently admits fifteen format families through sixteen suffixes;
  HTML and HTM are one family. Adding another format requires explicit parser,
  admission, limit, delivery, browser, and test evidence.
- CSV, HTML, legacy Office, OOXML, and OpenDocument behavior is testable inside
  the adapter without booting the worker.
- The 25 MB servlet limit is a transport ceiling. Product upload and chunk
  ceilings remain per-format and are part of deterministic processing state.
