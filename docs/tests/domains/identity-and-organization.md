# Identity And Organization Coverage

| Behavior | Evidence |
| --- | --- |
| JWT/session email and IdP roles are ignored in favor of explicit binding | `OidcCurrentActorProviderTests#resolvesOnlyTheExplicitIssuerSubjectBindingAndIgnoresJwtRolesAndEmail`, `#resolvesTheSameBindingForAnOidcBrowserSession` |
| Verified email and IdP admin role cannot bootstrap identity | `OidcCurrentActorProviderTests#rejectsVerifiedEmailAndAdminRoleWhenNoExplicitBindingExists` |
| Inactive linked users are denied | `OidcCurrentActorProviderTests#rejectsAnInactiveLinkedUser` |
| Session carries the app role for browser rendering | `BrowserSessionControllerTests#exposesOnlyTheCanonicalInternalActorForAnAuthenticatedSession` |
| Non-administrators are refused on every admin endpoint | `PermissionsAdminIntegrationTests#nonAdministratorsAreRefusedEverywhere` |
| Admin confirmation opens retrieval and revocation closes it | `#confirmingAnIdentityOpensRetrievalAndRevokingClosesIt` |
| Users report whether they can sign in at all | `#usersReportWhetherTheyCanSignInAtAll` |
| An administrator cannot change their own account | `#anAdministratorCannotChangeTheirOwnAccount` |
| Identity trust is recorded for the whole connection | `#identityTrustIsRecordedForTheWholeConnection` |
| Source groups report their sealed membership | `#sourceGroupsReportTheirSealedMembership` |
| The committed OpenAPI contract matches the live API | `OpenApiContractTests#theCommittedContractDescribesTheLiveApi` |

Gap: no self-service claim flow for end users (`selfClaim` still has no API
surface), no SCIM provisioning, and no browser test for the administration
screens — they are covered only through the API they call.
