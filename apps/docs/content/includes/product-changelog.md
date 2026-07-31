[//]: # (Generated from release/CHANGELOG.md by Tegami. Do not edit manually.)

## orgmemory@0.1.1

### Update the Skill CLI command parser

#### Improvements

Update the OrgMemory Skill CLI to Commander 15 while retaining its existing
ESM and Node 24 runtime contract.

### Update graph database runtime dependencies

#### Improvements

Update the Neo4j Java Driver to 6.2 for the graph storage adapter and refresh
the JUnit platform used by the backend test suite.

### Refresh Node application runtime dependencies

#### Improvements

Update the MCP CLI, Assistant web client, and public documentation runtime
dependencies to their latest compatible minor and patch releases.



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
