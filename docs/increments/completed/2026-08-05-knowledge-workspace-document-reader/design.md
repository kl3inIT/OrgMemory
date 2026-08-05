# Knowledge Workspace And Document Reader

## Problem

The employee navigation names `/sources` after only one of its two surfaces,
even though the workspace contains both Documents and Knowledge graph. The
Documents list already supports upload, filtering, governed view, and retirement,
but the evidence reader still feels incomplete:

- Markdown is delivered safely as `text/plain`, so classifying only the response
  blob loses the declared `text/markdown` presentation and shows raw source.
- The right-side reader fixes the preview to a bounded height instead of using
  the available viewport.
- Access subtitles infer audiences from classification (`Restricted` as
  `Executive only`, for example), even though effective access is governed by
  Knowledge Space/OpenFGA policy.
- Preview errors have no direct retry action, and the inline-format behavior is
  not protected together in a browser journey.

## Product Hierarchy

The visible workspace name is **Knowledge**. Its two peer tabs remain
**Documents** and **Knowledge graph**. The existing `/sources` route remains an
internal compatibility detail.

The selected document opens in a right-side evidence reader on desktop so list
context is preserved. On narrow screens the same reader consumes the available
width. A centered modal is rejected because it obscures both list context and
the vertical reading surface without adding a stronger decision boundary.

## Outcome

- Rename the employee navigation and route title to Knowledge while preserving
  the existing route and tab vocabulary.
- Make the evidence reader a viewport-filling flex surface with a wider desktop
  reading column and compact governed metadata.
- Render Markdown through the same restricted renderer as Assistant evidence,
  with Rendered and Raw views; block active HTML, remote images, and unsafe URLs.
- Keep exact PDF and safe raster images inline, plain text as text, and Office,
  HTML, SVG, JSON, XML, or unknown types download-only.
- Give preview failures an explicit retry without weakening `no-store`,
  permission rechecks, or the closed inline allowlist.
- Describe classification truthfully without inventing an effective audience.

## Constraints

- No backend, OpenAPI, authorization, ingestion, or persistence changes.
- The server-declared source media type may refine `text/plain` into Markdown;
  it may not upgrade any unsafe response into an active renderer.
- Reuse the existing Streamdown safety boundary, shadcn/Radix Sheet, semantic
  tokens, and page system. Add no dependency or visual subsystem.
- Preserve keyboard close, download, upload, delete confirmation, responsive
  behavior, and light/dark themes.

## Verification

- Unit tests pin the response-plus-declared-media classification matrix.
- Playwright proves the Knowledge navigation hierarchy, safe rendered/raw
  Markdown, PDF, image, plain-text, download-only Office, retry, deletion, and
  narrow-screen overflow behavior with deterministic authorized fixtures.
- Web lint, typecheck, unit tests, browser suite, production build, release
  policy, documentation model, and diff checks run on Node 24 where applicable.
