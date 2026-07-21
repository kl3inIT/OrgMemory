# Identity And Organization Coverage

| Behavior | Evidence |
| --- | --- |
| Client identity is ignored in favor of linked actor | `CapabilityAssetServiceIntegrationTests#createUsesLinkedActorInsteadOfClientSuppliedIdentity` |
| Unverified email cannot bootstrap identity | `#unverifiedEmailCannotBootstrapAnIdentityLink` |
| External identity linking is idempotent | `#externalIdentityLinkIsIdempotent` |
| Missing OrgMemory control role denies chat | `#chatRejectsAuthenticatedIdentityWithoutAnOrgMemoryRole` |

Gap: external source user/group resolution and OpenFGA tuple mapping do not exist.
