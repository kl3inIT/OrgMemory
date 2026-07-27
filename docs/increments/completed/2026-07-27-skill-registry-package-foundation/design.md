# Skill Registry Package Foundation

Status: completed on 2026-07-27 via PR #77.

## Outcome

OrgMemory accepts one portable Agent Skill package as a governed `SKILL` Asset.
The server validates the package before storage, keeps the original archive as
an immutable blob, and pins that blob by SHA-256 when an Asset revision and
release are created.

This increment establishes the package and lifecycle boundary. It does not add
the catalog/install UI, a local installer CLI, public publishing, ratings, or
MCP mutation tools.

## Package Contract

The upload is a ZIP containing one skill either at the archive root or in one
top-level directory. `SKILL.md` is required and follows the Agent Skills
frontmatter contract:

- `name`: lowercase letters, digits, and hyphens; at most 64 characters; equal
  to the containing directory name when the archive includes that directory.
- `description`: required, non-empty, at most 1024 characters.
- `license` and `compatibility`: optional strings.
- `metadata`: optional string-to-string map.
- `allowed-tools`: accepted as an experimental string field and preserved.

`allowed-tools` is package metadata only. OrgMemory never treats it as an
authorization grant or executes those tools during validation.

The remaining files are preserved as package content. Files are never executed
or rendered during validation.

## Security Boundary

Validation reads the ZIP central directory and file streams without extracting
to the filesystem. It rejects:

- absolute, parent-traversing, ambiguous, duplicate, or backslash paths;
- symbolic links, unreadable/encrypted entries, and entries outside the one
  skill root;
- more than 300 files, a compressed upload above 20 MiB, or more than 50 MiB of
  actual uncompressed content;
- a `SKILL.md` above 512 KiB, invalid UTF-8, invalid YAML, duplicate YAML keys,
  aliases, unexpected frontmatter shapes, or an invalid Agent Skills identity.

The API derives title, description, manifest, digest, and storage reference
from the inspected bytes. Clients cannot create or update `SKILL` Assets through
the generic JSON payload endpoint and therefore cannot claim an unvalidated blob
as a validated skill.

## Storage And Lifecycle

`SkillPackageStoragePort` is owned by the Asset Registry module. The MinIO
integration implements it using the existing object-storage infrastructure
without introducing a dependency from Asset Registry to the Knowledge module.

The mutable draft payload contains only server-generated portable metadata,
digest, size, and manifest; it never exposes the storage object key. The
internal `asset_payload_references` row owns that key and is created atomically
with the draft. Submission copies the reference to the immutable revision.
Publication copies the same exact reference and digest to the immutable release.

Replacing a draft package is intentionally deferred. PR1 supports a safe import
path and the existing review/release path; a later increment can add replacement
with explicit orphan cleanup and optimistic concurrency.

## API

`POST /api/assets/skills` consumes multipart form data:

- `file`: the ZIP package;
- `namespace`: company-local grouping;
- `knowledgeSpaceId`: governed parent;
- `classification`: OrgMemory classification, default `INTERNAL`.

It returns the ordinary `AssetView`, so review, approval, release history, and
authorization remain the common Asset lifecycle rather than a parallel Skill
registry.

## Research Basis

- Agent Skills specification and reference validator define the portable
  `SKILL.md` contract.
- Vercel Skills informed archive digest/path validation and bounded package
  handling.
- ClawHub informed registry lifecycle and security concerns, but its hosted
  Convex/Bun stack is not adopted.
- Apache Commons Compress `ZipFile` is used because central-directory access is
  required for duplicate, Unix mode, and symbolic-link checks.
