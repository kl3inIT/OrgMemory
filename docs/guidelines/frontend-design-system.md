# Frontend Design System Guideline

The replacement UI is an agent-first enterprise workspace, not a dashboard
template. Use shadcn/ui local components and maintained libraries for generic
primitives; custom code composes OrgMemory-specific evidence, permission, source,
candidate, and approval workflows.

Required qualities:

- light and dark themes using semantic tokens;
- visible pending, streaming, indexing, denied, stale, failed, and retry states;
- keyboard navigation and accessible names/focus;
- responsive behavior from narrow laptop windows through large admin screens;
- citations and evidence provenance adjacent to generated claims;
- privacy/source status understandable without exposing restricted metadata.

Do not copy old page layouts merely to preserve route parity. Reuse old code only
when it is generic, tested, and compatible with the new information architecture.
