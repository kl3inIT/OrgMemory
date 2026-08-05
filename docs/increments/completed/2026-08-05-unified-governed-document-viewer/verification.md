# Unified Governed Document Viewer Verification

## Delivered

- One `GovernedDocumentViewer` owns source/citation loading, responsive dialog
  composition, format rendering, safe Markdown, fallback states, object URLs,
  copy, and download behavior.
- Knowledge Documents stays full width and opens the shared centered viewer.
- Assistant inline citations open the viewer in one click; the aggregate source
  control still opens the right source sidebar, whose rows use the same viewer.
- Citation evidence remains excerpt-first and permission-rechecked. Source
  documents continue through their canonical permission-verified content route.

## Reference

Verified against pinned Onyx revision
`618b5031bf21463f44e3bed9eb9d5073b806fec0`, specifically its shared
`openDocument` -> `presentingDocument` -> `PreviewModal` path and format
variants. OrgMemory retained its stricter source/citation endpoint separation.

## Gates

All frontend commands ran with Node `v24.15.0`:

```text
pnpm --filter @orgmemory/web lint                         PASS
pnpm --filter @orgmemory/web typecheck                   PASS
pnpm --filter @orgmemory/web test:unit                   PASS (67 tests)
pnpm --filter @orgmemory/web build                       PASS
pnpm --filter @orgmemory/web test:e2e                    PASS (31 tests)
focused centered-viewer capture                          PASS (1 test)
pnpm release:check                                      PASS (41 tests plus Tegami check)
git diff --check                                         PASS
```

The production build retains the repository's non-blocking large-chunk warning;
this increment adds no dependency. Browser verification covered desktop dark
mode at `1459 x 816`, mobile overflow at `390 x 844`, both viewer entry points,
all supported preview kinds, access revocation, and safe Markdown behavior.
