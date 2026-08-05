# Agentic Skill Beta Plan

## Status

Active.

## Steps

- [x] Audit the existing Skill package, distribution, Assistant, MCP, and model
  gateway boundaries.
- [x] Record the architecture challenge, strongest counterargument, fallback,
  and owner direction.
- [ ] Add a bounded actor-scoped Skill runtime catalog, activation, and resource
  reader above exact authorized releases.
- [ ] Connect those operations to a request-local Spring AI 2 tool-calling loop.
- [ ] Stream truthful Skill discovery/activation/resource activity to the web UI.
- [ ] Add focused security and behavior tests.
- [ ] Reconcile Assistant and Asset Registry specs/test matrices and complete
  repository verification.

## Out of scope

- server-side script, shell, binary, or arbitrary package execution;
- dynamic permission/tool grants from `allowed-tools`;
- a second Skill registry or filesystem mirror;
- long-running autonomous jobs, checkpoints, browser automation, and MCP writes.

