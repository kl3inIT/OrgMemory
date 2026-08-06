# Identity And Organization Coverage

Source: `core/src/test/java/com/orgmemory/core/organization`,
`core/src/test/java/com/orgmemory/core/identityprovisioning`,
`apps/api/src/test/java/com/orgmemory/api/organization`,
`apps/api/src/test/java/com/orgmemory/api/security`,
`apps/api/src/test/java/com/orgmemory/api/scim`,
`apps/api/src/test/java/com/orgmemory/api/admin`, and `apps/web/test/e2e`.

Reconciled: `2026-08-06-clearance-separation (219902eb)`.

| Behavior | Evidence |
| --- | --- |
| JWT/session email and IdP roles are ignored in favor of explicit binding | `OidcCurrentActorProviderTests#resolvesOnlyTheExplicitIssuerSubjectBindingAndIgnoresJwtRolesAndEmail`, `#resolvesTheSameBindingForAnOidcBrowserSession` |
| Uninvited verified email and IdP admin role cannot bootstrap identity | `OidcCurrentActorProviderTests#rejectsVerifiedEmailAndAdminRoleWhenNoExplicitBindingExists` |
| Exactly one invitation provisions a subject; zero or cross-organization ambiguity provisions nothing | `UserProvisioningServiceTests#anInvitedAddressBecomesAUserBoundToItsSubject`, `#anAddressWithNoInvitationProvisionsNothing`, `#anAddressExpectedByTwoOrganizationsProvisionsNothing` |
| Binding verifies both unique-key winners and returns opaque conflicts | `ExternalIdentityBindingServiceTests` |
| Fifty concurrent first logins leave one binding and one accepted invitation | `IdentityBindingConcurrencyIntegrationTests#concurrentFirstLoginLeavesOneBindingAndOneAcceptedInvitation` |
| Tenant foreign keys, organization-scoped email uniqueness, and the six-value role to two-value clearance backfill survive populated upgrades | `IdentityTenantIntegrityMigrationTests`, `IdentityOrganizationEmailCutoverMigrationTests`, `ClearanceSeparationMigrationTests` |
| Inactive linked users are denied | `OidcCurrentActorProviderTests#rejectsAnInactiveLinkedUser` |
| Knowledge reads reload the active persisted department and Executive state; ADMIN, inactive, and foreign-tenant subjects cannot widen access | `JpaKnowledgeAccessSubjectQueryTests`, `ModulithVerificationTests#retrievalDoesNotDependOnOrganizationPersistenceOrRoleTypes`, `#retrievalOrganizationReadsUseOnlyOwnerQueries`, `KnowledgeRetrievalIntegrationTests` |
| Authorization resource resolution uses Organization-owned canonical organization and department existence queries | `JpaOrganizationResourceQueryTests`, `PermissionsAdminIntegrationTests` |
| Session carries clearance and an OpenFGA-derived member-administration affordance without conflating them | `BrowserSessionControllerTests#exposesOnlyTheCanonicalInternalActorForAnAuthenticatedSession` |
| Non-administrators are refused on every admin endpoint | `PermissionsAdminIntegrationTests#nonAdministratorsAreRefusedEverywhere` |
| Admin confirmation opens retrieval and revocation closes it | `#confirmingAnIdentityOpensRetrievalAndRevokingClosesIt` |
| Users report whether they can sign in at all | `#usersReportWhetherTheyCanSignInAtAll` |
| Administrators can raise clearance, assign or explicitly clear an in-organization department, cannot assign a foreign department, and cannot change their own account | `PermissionsAdminIntegrationTests` clearance and department PATCH cases |
| `/api/me` reports the current user's department name and clearance without a legacy role field | `PermissionsAdminIntegrationTests#meReportsDepartmentNameAndClearance` |
| Identity trust is recorded for the whole connection | `#identityTrustIsRecordedForTheWholeConnection` |
| Source groups report their active membership snapshot and generation | `#sourceGroupsReportTheirSealedMembership` |
| The committed product and SCIM OpenAPI contracts match their live groups | `OpenApiContractTests` |

Browser-authentication contracts additionally prove anonymous session denial,
issuer/subject parity between OIDC sessions and bearer JWTs, invitation-only
first sign-in, inactive-user denial, CSRF token publication, exact provider
logout, dev-only Swagger, and production startup guards. The live browser
checks cover protected-route redirect, login, JDBC session restore, theme
switching, local plus provider logout, read-only account clearance/department
context, Executive blast-radius confirmation, and department assignment
(`admin-users-clearance.spec.ts`).

Gaps: `selfClaim` still has no end-user API, and SCIM User and Group mutations
are not exposed.
