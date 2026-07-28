# Identity And Organization Coverage

Source: `core/src/test/java/com/orgmemory/core/organization`,
`core/src/test/java/com/orgmemory/core/identityprovisioning`,
`apps/api/src/test/java/com/orgmemory/api/organization`,
`apps/api/src/test/java/com/orgmemory/api/security`,
`apps/api/src/test/java/com/orgmemory/api/scim`,
`apps/api/src/test/java/com/orgmemory/api/admin`, and `web/test/e2e`.

Reconciled: `2026-07-29-repository-operating-model-refresh (7cf1c8a)`.

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
| Source groups report their active membership snapshot and generation | `#sourceGroupsReportTheirSealedMembership` |
| The committed product and SCIM OpenAPI contracts match their live groups | `OpenApiContractTests` |

Browser-authentication contracts additionally prove anonymous session denial,
issuer/subject parity between OIDC sessions and bearer JWTs, invitation-only
first sign-in, inactive-user denial, CSRF token publication, exact provider
logout, dev-only Swagger, and production startup guards. The live browser
check covers protected-route redirect, login, JDBC session restore, theme
switching, and local plus provider logout.

Gaps: `selfClaim` still has no end-user API; SCIM User and Group mutations are
not exposed; and the administration screens have no dedicated browser test
beyond the APIs and shared shell they use.
