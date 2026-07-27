# F1 Protocol Dependency Decision

Date: 2026-07-27

## Decision

Select only `com.unboundid.product.scim2:scim2-sdk-common:6.0.0` for the future
SCIM protocol adapter. Do not use its client or server modules. Spring MVC
remains the HTTP runtime, and OrgMemory owns attribute allowlists, resource
lifecycle, query translation, limits, errors, and truthful discovery.

Version 6.0.0 is the first compared Ping release built for Jackson 3. Its common
module provides the filter AST, SCIM path/PATCH models, protocol resources, and
error models without adding JAX-RS. The isolated `pingCandidate` Gradle
configuration fails the build if Jackson 2, JAX-RS, Jersey, or RESTEasy enters
the selected dependency graph.

The artifact is available under Apache License 2.0 as well as legacy license
choices. OrgMemory consumes it under Apache License 2.0. This is an engineering
license compatibility record, not external legal advice.

## Candidate Comparison

| Gate | Ping SCIM SDK common 6.0.0 | Apache SCIMple 1.0.0-M1 |
| --- | --- | --- |
| Boot 4/Jackson 3 | Native Jackson 3.1.x | Released M1 uses Jackson 2.16 and Boot 3.2 |
| Filter AST | Passes the executable corpus | Parses the common subset with ANTLR |
| PATCH paths | Case-insensitive op JSON, pathless and provider paths pass | Path parser handles the common membership form |
| HTTP runtime | Common module has none | Schema module has none; server choices add another runtime |
| Maintenance | 6.0.0 released in 2026 | Only M1 is released; Boot 4 work is unreleased |
| License | Apache 2.0 selected | Apache 2.0 |
| Decision | Selected, common module only | Rejected for production |

Apache SCIMple remains useful comparison evidence, but selecting an unreleased
snapshot or carrying Jackson 2 beside the application runtime is not justified.

## Security And Dependency Gate

- Filter text, AST depth, and AST node count are bounded before query
  translation.
- Every AST attribute and operator is allowlisted during translation; parsing
  does not authorize an attribute.
- Invalid grammar never falls back to an unfiltered query.
- PATCH is deserialized into typed operations and paths; no regular-expression
  parser is introduced.
- Bulk support present in a library does not make Bulk an OrgMemory capability.
- Before F3 runtime adoption, the repository dependency/CVE scanner must run
  again on the exact locked graph. The F1 comparison found no reason to add a
  second HTTP runtime; scanner results are time-sensitive and remain a release
  gate rather than a permanent “no CVE” claim.

## Primary Sources

- [Ping SCIM SDK 6.0.0 release](https://github.com/pingidentity/scim2/releases/tag/scim2-6.0.0)
- [Ping SCIM SDK license](https://github.com/pingidentity/scim2/blob/scim2-6.0.0/LICENSE.md)
- [Apache SCIMple 1.0.0-M1](https://central.sonatype.com/artifact/org.apache.directory.scimple/scim-spec-schema/1.0.0-M1)
- [Apache SCIMple repository](https://github.com/apache/directory-scimple)
