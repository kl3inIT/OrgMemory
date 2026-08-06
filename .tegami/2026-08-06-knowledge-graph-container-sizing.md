---
packages:
  orgmemory: patch
subject: Keep the knowledge graph stable while its panel is sizing
---

## Fixes

The knowledge graph now waits for a visible, positively sized canvas before
starting Sigma, and releases the renderer if the panel becomes size-less during
a layout transition. Opening the graph while its tab or flex layout is still
settling no longer crashes the page with a zero-height container error.
