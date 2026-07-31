# OrgMemory changelog

Product releases are assembled from reviewed entries under `.tegami/`.

## orgmemory@0.1.0

### Changelog layout

#### Documentation

Keep the product changelog title first and render each reviewed release entry
with one clear subject heading.

### Release evidence secret scanning

#### Security

Keep full-history secret scanning enabled while recognizing immutable GHCR
references whose public image tags contain Git commit SHAs.

### Product release management

#### Operations

Add reviewed product changelogs, semantic versions, immutable artifact
manifests, and GitHub Releases without changing the SHA-addressed deployment
pipeline.

### Shared ZM team development

#### Operations

Let developers run local OrgMemory applications against the shared
non-production ZM services through private SSH tunnels, with automatic worker
coordination and guarded post-merge schema/model updates.

### Version pull request preflight

#### Fixes

Allow Tegami to validate a generated Version PR commit while retaining the
independent current-main checks around writable release operations.
