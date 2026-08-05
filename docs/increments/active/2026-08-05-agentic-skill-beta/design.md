# Agentic Skill Beta

## Intent

Make the existing Assistant able to discover and activate governed Agent Skills
without creating a second Skill registry or pretending that the server can
execute arbitrary Skill packages safely.

The Asset Registry remains the source of truth. Its `SKILL` type already owns
Agent Skills-compatible `SKILL.md` validation, immutable release coordinates,
package digests, authorization, and MCP/CLI distribution. This increment adds
an actor-scoped runtime view and a bounded model tool loop above that contract.

## Decision

The beta exposes three read-only operations to the Assistant model:

1. `search_skills` returns a small actor-authorized metadata catalog with exact
   asset and release identifiers.
2. `activate_skill` returns the exact release's bounded `SKILL.md` instructions.
3. `read_skill_resource` returns one bounded text resource from the same exact
   release.

Every operation re-enters the existing live authorization path. A search result
does not grant later access. Denials stay opaque. Object-storage keys and raw ZIP
bytes never enter model context.

Skill content is untrusted context. `allowed-tools` remains descriptive metadata
and never grants a Spring bean, MCP tool, or permission. The model receives only
the fixed read-only beta tool set selected by the server.

The runtime does not execute scripts, binaries, shell commands, or package code.
External agents may continue installing the exact package through MCP/CLI and
execute it in their own governed environment.

## Compatibility

The package contract follows the Agent Skills progressive-disclosure shape:
metadata can be listed cheaply, instructions are activated on demand, and
supporting resources are read only when needed. Loading packages from
S3-compatible storage does not change the package standard; storage is an
implementation detail behind the same exact-release contract.

The implementation uses Spring AI 2.0 `ToolCallingAdvisor` through request-local
tools. It does not use the deprecated `ToolCallAdvisor`, Spring AI Alibaba's
filesystem registry, or `spring-ai-agent-utils` filesystem/shell tools.

## Safety boundaries

- No sandbox means no server-side package execution.
- Only UTF-8 text resources declared in the inspected package manifest are
  readable; per-resource and aggregate bounds apply.
- Archive paths are exact safe relative paths; no traversal or case folding.
- Exact release digest and stored package integrity checks remain mandatory.
- Tool results contain no denied metadata, credentials, storage references, or
  arbitrary exception text.
- Existing retrieval remains permission-first. Skill activation augments how the
  model works; it does not replace evidence authorization or citation rules.
- A model response without tool calls retains the ordinary text path. Provider
  compatibility with the fixed tool schemas remains an administrator concern.

## Reference evidence

- `docs/vision.md` defines a Skill Registry as the filtered installable view of
  the shared catalog.
- `core.assetregistry.skill` owns the canonical bounded package profile and
  exact release distribution.
- Agent Skills client guidance defines metadata listing, explicit activation,
  and resource reads as a valid client integration without direct filesystem
  access.
- Spring AI 2.0 documents `ToolCallingAdvisor` as the recursive streaming tool
  loop and request-local `tools(...)` as the runtime registration surface.

## Exit criteria

- The model can search, activate, and read a governed Skill through a real tool
  loop while the user sees truthful activity.
- Unauthorized/cross-tenant releases remain opaque at every operation.
- Tests prove archive bounds, UTF-8 handling, exact release pinning, and no tool
  authority derived from Skill metadata.
- Assistant and Asset Registry specs/test matrices describe the implemented
  boundary.
