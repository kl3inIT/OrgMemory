# OrgMemory Docs Guidance

Read the repository root `CLAUDE.md` first, then this file and
`apps/docs/ARCHITECTURE.md`.

- Public prose lives only in `content/docs`; internal engineering documents are
  source evidence, not publication input.
- Every page must be listed in `public-content.manifest.json` and carry the
  required typed frontmatter.
- `sourceRefs` is build-time traceability and must never be rendered.
- Drafts remain excluded unless `DOCS_INCLUDE_DRAFTS=true` is set for a local or
  controlled preview.
- English is the default publication language: `page.mdx` remains at `/docs/...`.
  Add Vietnamese one reviewed page at a time as `page.vi.mdx`; its route is
  `/vi/docs/...`. Do not bulk-copy or machine-publish untranslated pages.
- Vietnamese routes fall back visibly to the reviewed English page until the
  matching `.vi.mdx` exists. A fallback route is not a completed translation.
- Keep root-folder names and descriptions aligned between `meta.json` and
  `meta.vi.json`; these files define the sidebar documentation switcher.
- Verify unfamiliar Next.js and Fumadocs APIs against current official
  documentation or installed dependency types before use.
- Run `pnpm --filter @orgmemory/docs check` and `build` before handoff.
