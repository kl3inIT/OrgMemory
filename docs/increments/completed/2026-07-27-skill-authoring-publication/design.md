# Skill Authoring And Draft Publication

Status: active.

## Problem

OrgMemory can govern, release, discover, and install a Skill, but the first
authoring step is still an administrative ZIP upload. That exposes a transport
artifact as the employee workflow and leaves local Skill authors without a
fast validation command.

The missing journey is:

```text
Skill folder -> local validation -> authenticated Draft -> review -> release
             -> exact-version discovery and installation
```

## Outcome

- Add `orgmemory skill validate <folder>` as an offline preflight.
- Add `orgmemory skill publish <folder>` for an authenticated, company-local
  Draft publication.
- Build the ZIP deterministically inside the CLI. Authors never prepare or
  upload a ZIP themselves.
- Keep the server validator authoritative and reuse the existing `SKILL` Asset
  creation, review, approval, release, and immutable package-reference path.
- Return the created Asset and Draft identity so the author can continue in
  Governance.

## Publication Boundary

MCP tools remain read-only. Publication uses an authenticated HTTP companion
endpoint on the same OAuth protected resource:

```text
CLI --OAuth/PKCE, assets:read + assets:write--> publication gateway
publication gateway --RFC 8693 token exchange--> canonical Asset API
canonical Asset API --> live CAN_CREATE_ASSET + Skill package validator
```

The gateway never owns Skill lifecycle or persistence. It forwards one bounded
multipart request to `POST /api/assets/skills`; the canonical API and Core
remain authoritative.

`assets:write` authorizes access to the publication transport, not publication
itself. Core still evaluates the actor's current organization and Knowledge
Space relationship before creating the Draft.

## Local Package Contract

The CLI accepts one directory containing root `SKILL.md`. It:

- rejects symbolic links, unsupported filesystem entries, unsafe or
  case-colliding relative paths;
- enforces the same 300-file, 20 MiB archive, 50 MiB content, and 512 KiB
  `SKILL.md` bounds as the server;
- validates UTF-8 and the supported Agent Skills frontmatter shape;
- sorts paths and fixes ZIP timestamps and attributes so identical folders
  produce identical bytes and SHA-256 digests;
- reports name, description, file count, content bytes, archive bytes, and
  package digest.

Local validation is for fast feedback only. The server repeats validation and
derives all stored metadata from the uploaded bytes.

## Commands

```text
orgmemory skill validate ./skills/expense-review

orgmemory skill publish ./skills/expense-review \
  --namespace finance \
  --knowledge-space <uuid>

orgmemory skill publish ./skills/expense-review \
  --namespace finance \
  --knowledge-space <uuid> \
  --classification INTERNAL \
  --dry-run \
  --json
```

`--dry-run` performs the complete local inspection and deterministic package
build without OAuth or a network request.

## Scope Decisions

- This increment creates a new Skill Draft. Replacing an existing Skill Draft
  remains separate because the old blob may already be pinned by immutable
  revisions or releases and therefore needs reference-aware cleanup plus
  optimistic concurrency.
- No automatic submit, approve, or release. The author must use the ordinary
  Governance workflow.
- No MCP mutation tool, public marketplace, ratings, arbitrary Git execution,
  or browser-first ZIP upload workflow.
- The project owner's standing instruction for this sequence waives a separate
  Claude Fable 5 review. The strongest counterargument is a second dedicated
  OAuth protected resource for publication. It is rejected for this increment
  because the scope-gated HTTP companion keeps one DCR/token-exchange gateway
  while MCP capabilities themselves remain read-only.

## Research Basis

- Agent Skills defines the portable `SKILL.md` contract.
- ClawHub informed folder-first publish, dry-run, bounded file collection, and
  provenance-oriented output.
- Vercel Skills informed local folder discovery and deterministic file
  identity.
- OrgMemory's existing package inspector remains the security authority.
