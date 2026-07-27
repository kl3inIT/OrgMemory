# Identity And Organization Coverage

| Behavior | Evidence |
| --- | --- |
| JWT/session email and IdP roles are ignored in favor of explicit binding | `OidcCurrentActorProviderTests#resolvesOnlyTheExplicitIssuerSubjectBindingAndIgnoresJwtRolesAndEmail`, `#resolvesTheSameBindingForAnOidcBrowserSession` |
| Uninvited verified email and IdP admin role cannot bootstrap identity | `OidcCurrentActorProviderTests#rejectsVerifiedEmailAndAdminRoleWhenNoExplicitBindingExists` |
| Exactly one invitation provisions a subject; zero or cross-organization ambiguity provisions nothing | `UserProvisioningServiceTests#anInvitedAddressBecomesAUserBoundToItsSubject`, `#anAddressWithNoInvitationProvisionsNothing`, `#anAddressExpectedByTwoOrganizationsProvisionsNothing` |
| Binding verifies both unique-key winners and returns opaque conflicts | `ExternalIdentityBindingServiceTests` |
| Fifty concurrent first logins leave one binding and one accepted invitation | `IdentityBindingConcurrencyIntegrationTests#concurrentFirstLoginLeavesOneBindingAndOneAcceptedInvitation` |
| Tenant foreign keys and organization-scoped email uniqueness survive populated upgrades | `IdentityTenantIntegrityMigrationTests`, `IdentityOrganizationEmailCutoverMigrationTests` |
| Inactive linked users are denied | `OidcCurrentActorProviderTests#rejectsAnInactiveLinkedUser` |
| Session carries the app role for browser rendering | `BrowserSessionControllerTests#exposesOnlyTheCanonicalInternalActorForAnAuthenticatedSession` |
| Non-administrators are refused on every admin endpoint | `PermissionsAdminIntegrationTests#nonAdministratorsAreRefusedEverywhere` |
| Admin confirmation opens retrieval and revocation closes it | `#confirmingAnIdentityOpensRetrievalAndRevokingClosesIt` |
| Users report whether they can sign in at all | `#usersReportWhetherTheyCanSignInAtAll` |
| An administrator cannot change their own account | `#anAdministratorCannotChangeTheirOwnAccount` |
| Identity trust is recorded for the whole connection | `#identityTrustIsRecordedForTheWholeConnection` |
| Source groups report their sealed membership | `#sourceGroupsReportTheirSealedMembership` |
| The committed OpenAPI contract matches the live API | `OpenApiContractTests#theCommittedContractDescribesTheLiveApi` |

Gap: no self-service source-principal claim flow for end users (`selfClaim`
still has no API surface), no SCIM provisioning, and no browser test for the
administration screens — they are covered only through the API they call.
