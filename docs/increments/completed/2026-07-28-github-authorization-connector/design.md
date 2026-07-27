# GitHub authorization connector

Date: 2026-07-28

## Outcome

Add GitHub as the third live connector and prove that the source-authorization
kernel is not Slack- or Drive-shaped. The connector indexes issues and pull
requests from private organization repositories and mirrors GitHub's effective
readers without copying provider semantics into `core`.

The exit proof is a reader removed through a GitHub team: the next successful
membership crawl revokes OrgMemory retrieval without changing the work item's
ACL generation, content revision, chunks, or embeddings.

## Official capability evidence

Context7 was attempted first as required, but its monthly quota was exhausted.
The capability decision therefore uses current GitHub documentation directly:

- [List repository collaborators](https://docs.github.com/en/rest/collaborators/collaborators?apiVersion=2026-03-10)
  includes direct collaborators, team-derived members, organization default
  permissions, organization owners, and enterprise-level grants. Its
  `role_name` is the highest effective role after all grant sources.
- [GitHub REST pagination](https://docs.github.com/en/rest/using-the-rest-api/using-pagination-in-the-rest-api)
  requires following `Link` relations until there is no `rel="next"`.
- [GitHub App installation tokens](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-an-installation-access-token-for-a-github-app)
  are minted with an app JWT, expire after one hour, and inherit only the
  installation's repositories and permissions.
- [GitHub App JWTs](https://docs.github.com/en/apps/creating-github-apps/authenticating-with-a-github-app/generating-a-json-web-token-jwt-for-a-github-app)
  use RS256, backdate `iat` for clock drift, and expire within ten minutes.
- [List repository issues](https://docs.github.com/en/rest/issues/issues?apiVersion=2026-03-10)
  returns both issues and pull requests and supports `state=all`.

The GitHub App needs repository `Metadata: read` and `Issues: read`. Metadata
read covers installation repositories and effective collaborators. No
organization-members or repository-administration permission is required.

## Authorization mapping

GitHub exposes the complete effective reader set but cannot attribute every
reader to exactly one source: a user may read through a direct grant, team,
organization base permission, owner status, or an enterprise/custom role.
Attempting to rebuild those paths would create an incomplete second GitHub
authorization engine.

For each repository:

- source user native ID: GitHub's immutable numeric user `id`;
- source group native ID: `repository:{numericRepositoryId}:readers`;
- source group membership: every entry from the fully paginated effective
  collaborators endpoint;
- issue/PR ACL: one `ALLOW` grant to that repository reader group;
- user email: absent, because this API does not vouch for an email address;
- AppUser binding: explicit administrator confirmation or another trusted
  identity join, never GitHub login-string guessing.

The source group is an effective source entitlement set, not a claim that
GitHub has a team with that name. Its key is derived only from the provider's
stable numeric repository ID. This preserves every GitHub access path and lets
membership change independently from resource ACL evidence.

## Supported boundary

- GitHub.com organization installations only for this increment.
- Private repositories with Issues enabled.
- Issues and pull requests are content objects; comments, code, discussions,
  projects, and public/internal repository visibility are outside this
  connector profile.
- Optional configured repository IDs narrow scope intentionally. Empty scope
  means all admissible repositories visible to the installation.
- Public/internal or user-owned repositories are not silently interpreted with
  a narrower ACL. The scope browser marks them inadmissible, and an explicitly
  configured inadmissible repository rejects the crawl.

## Credential and transport

The stored credential is the GitHub App installation material:

```json
{
  "appId": "123456",
  "installationId": "789012",
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----"
}
```

It is parsed into a redacted value object. A short app JWT mints an installation
token, which is cached with an expiry margin and never appears in a URI, log,
response, or `toString`. The REST client:

- sends `Accept: application/vnd.github+json` and
  `X-GitHub-Api-Version: 2026-03-10`;
- follows GitHub `Link` pagination rather than guessing page counts;
- bounds response bodies;
- retries bounded 429, exhausted-rate-limit 403, transport drops, and 5xx;
- reports stable provider/adapter error codes without credential material.

The credential probe verifies the installation exists, targets an organization,
has the required permissions, and can see at least one repository. The returned
connection key is the installation account's immutable numeric GitHub ID.

## Crawl behavior

A content crawl reads installation repositories, fully captures effective
reader membership for every repository in scope, and reads all issue/PR
descriptions within the configured bound. A permission-only crawl re-reads
repository readers and restates grants for existing ledger objects without
reading issue bodies.

Component cursors are independent:

- `CONTENT`: object ID plus content revision and enumeration completeness;
- `PERMISSION`: repository-to-reader-group grants;
- `MEMBERSHIP`: repository reader group plus sorted numeric user IDs.

An incomplete or failed collaborator enumeration never activates membership
and never rotates an ACL. Content truncation makes `CONTENT` incomplete without
falsely downgrading fully captured permission evidence.

## Strongest counterargument and decision

The strongest alternative is to model GitHub teams directly and grant every
issue to repository teams plus direct users. That is attractive because a team
looks like Slack's channel group, but it is not authorization-complete:
organization default permissions, owners, custom/enterprise roles, nested teams,
and users with overlapping direct/team grants cannot be reconstructed without
either widening or wrongly revoking access.

Decision: mirror GitHub's authoritative effective collaborator set as one
repository entitlement group. Preserve provider-native user and repository IDs,
and do not pretend OrgMemory knows which overlapping GitHub grant caused access.

The repository convention normally requires an independent Claude Fable 5
architecture debate for this decision. The project owner explicitly waived that
step for this session because the Claude quota is exhausted. The strongest
counterargument and its rejection are recorded here instead.

## Failure semantics

- malformed app credential: connection unavailable, `invalid_key`;
- missing installation or suspended/revoked app: connection unavailable with
  GitHub status-derived code;
- missing `issues:read`: probe returns content access unavailable;
- collaborator pagination failure: no authoritative membership batch;
- issue pagination truncated by configured bound: `CONTENT=INCOMPLETE`;
- unknown visibility/owner type in configured scope: reject, never infer public
  or enterprise-wide access;
- unknown/unmapped GitHub user: grants nothing until an AppUser binding exists.
