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
- Verify unfamiliar Next.js and Fumadocs APIs against current official
  documentation or installed dependency types before use.
- Run `pnpm --filter @orgmemory/docs check` and `build` before handoff.
