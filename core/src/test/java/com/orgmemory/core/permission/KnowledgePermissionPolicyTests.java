package com.orgmemory.core.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class KnowledgePermissionPolicyTests {

    private final KnowledgePermissionPolicy policy = new KnowledgePermissionPolicy();

    @ParameterizedTest
    @EnumSource(KnowledgeRole.class)
    void publicAndInternalAllowEveryActiveBusinessRole(KnowledgeRole role) {
        assertAllowed(role, KnowledgeClassification.PUBLIC, DeclaredAccessScope.ALL, "FIN", "OPS");
        assertAllowed(role, KnowledgeClassification.INTERNAL, DeclaredAccessScope.ALL_EMPLOYEES, "FIN", "OPS");
    }

    @ParameterizedTest
    @EnumSource(value = KnowledgeRole.class, names = {"EMPLOYEE", "MANAGER", "DIRECTOR"})
    void confidentialRequiresOwnDepartmentForNonExecutives(KnowledgeRole role) {
        PermissionDecision sameDepartment = evaluate(
                subject(role, "FIN", true),
                resource(KnowledgeClassification.CONFIDENTIAL, DeclaredAccessScope.OWN_DEPARTMENT, "FIN"),
                AccessGate.ALLOW,
                AccessGate.ALLOW);
        PermissionDecision otherDepartment = evaluate(
                subject(role, "FIN", true),
                resource(KnowledgeClassification.CONFIDENTIAL, DeclaredAccessScope.OWN_DEPARTMENT, "OPS"),
                AccessGate.ALLOW,
                AccessGate.ALLOW);

        assertTrue(sameDepartment.allowed());
        assertEquals(PermissionReason.CONFIDENTIAL_SAME_DEPARTMENT, sameDepartment.reason());
        assertFalse(otherDepartment.allowed());
        assertEquals(PermissionReason.CONFIDENTIAL_OTHER_DEPARTMENT, otherDepartment.reason());
    }

    @Test
    void executiveCanReadCrossDepartmentConfidentialAndRestricted() {
        PermissionDecision confidential = evaluate(
                subject(KnowledgeRole.EXECUTIVE, "EXEC", true),
                resource(KnowledgeClassification.CONFIDENTIAL, DeclaredAccessScope.OWN_DEPARTMENT, "FIN"),
                AccessGate.ALLOW,
                AccessGate.ALLOW);
        PermissionDecision restricted = evaluate(
                subject(KnowledgeRole.EXECUTIVE, "EXEC", true),
                resource(KnowledgeClassification.RESTRICTED, DeclaredAccessScope.EXECUTIVE_ONLY, "EXEC"),
                AccessGate.ALLOW,
                AccessGate.ALLOW);

        assertEquals(PermissionReason.CONFIDENTIAL_EXECUTIVE, confidential.reason());
        assertEquals(PermissionReason.RESTRICTED_EXECUTIVE, restricted.reason());
        assertTrue(confidential.allowed());
        assertTrue(restricted.allowed());
    }

    @Test
    void sourceAndOrgMemoryDenialsOverrideExecutiveRole() {
        KnowledgeSubject executive = subject(KnowledgeRole.EXECUTIVE, "EXEC", true);
        KnowledgeResource restricted = resource(
                KnowledgeClassification.RESTRICTED, DeclaredAccessScope.EXECUTIVE_ONLY, "EXEC");

        assertEquals(
                PermissionReason.SOURCE_PERMISSION_DENIED,
                evaluate(executive, restricted, AccessGate.DENY, AccessGate.ALLOW).reason());
        assertEquals(
                PermissionReason.SOURCE_PERMISSION_UNKNOWN,
                evaluate(executive, restricted, AccessGate.UNKNOWN, AccessGate.ALLOW).reason());
        assertEquals(
                PermissionReason.ORGMEMORY_PERMISSION_DENIED,
                evaluate(executive, restricted, AccessGate.ALLOW, AccessGate.DENY).reason());
        assertEquals(
                PermissionReason.ORGMEMORY_PERMISSION_UNKNOWN,
                evaluate(executive, restricted, AccessGate.ALLOW, AccessGate.UNKNOWN).reason());
    }

    @Test
    void organizationMismatchInactiveSubjectAndMetadataConflictFailClosed() {
        PermissionDecision wrongOrganization = policy.evaluate(new KnowledgeAccessRequest(
                subject(KnowledgeRole.EXECUTIVE, "EXEC", true),
                new KnowledgeResource(
                        "other-org", "DOC001", "EXEC", KnowledgeClassification.PUBLIC, DeclaredAccessScope.ALL),
                AccessGate.ALLOW,
                AccessGate.ALLOW));
        PermissionDecision inactive = evaluate(
                subject(KnowledgeRole.EXECUTIVE, "EXEC", false),
                resource(KnowledgeClassification.PUBLIC, DeclaredAccessScope.ALL, null),
                AccessGate.ALLOW,
                AccessGate.ALLOW);
        PermissionDecision conflictingMetadata = evaluate(
                subject(KnowledgeRole.EXECUTIVE, "EXEC", true),
                resource(KnowledgeClassification.RESTRICTED, DeclaredAccessScope.ALL_EMPLOYEES, "EXEC"),
                AccessGate.ALLOW,
                AccessGate.ALLOW);

        assertEquals(PermissionReason.ORGANIZATION_MISMATCH, wrongOrganization.reason());
        assertEquals(PermissionReason.INACTIVE_SUBJECT, inactive.reason());
        assertEquals(PermissionReason.DECLARED_ACCESS_MISMATCH, conflictingMetadata.reason());
    }

    @Test
    void confidentialMissingDepartmentFailsClosed() {
        PermissionDecision missingSubjectDepartment = evaluate(
                subject(KnowledgeRole.EMPLOYEE, null, true),
                resource(KnowledgeClassification.CONFIDENTIAL, DeclaredAccessScope.OWN_DEPARTMENT, "FIN"),
                AccessGate.ALLOW,
                AccessGate.ALLOW);
        PermissionDecision missingResourceDepartment = evaluate(
                subject(KnowledgeRole.EMPLOYEE, "FIN", true),
                resource(KnowledgeClassification.CONFIDENTIAL, DeclaredAccessScope.OWN_DEPARTMENT, null),
                AccessGate.ALLOW,
                AccessGate.ALLOW);
        PermissionDecision executiveMissingSubjectDepartment = evaluate(
                subject(KnowledgeRole.EXECUTIVE, null, true),
                resource(KnowledgeClassification.CONFIDENTIAL, DeclaredAccessScope.OWN_DEPARTMENT, "FIN"),
                AccessGate.ALLOW,
                AccessGate.ALLOW);
        PermissionDecision executiveMissingResourceDepartment = evaluate(
                subject(KnowledgeRole.EXECUTIVE, "EXEC", true),
                resource(KnowledgeClassification.CONFIDENTIAL, DeclaredAccessScope.OWN_DEPARTMENT, null),
                AccessGate.ALLOW,
                AccessGate.ALLOW);

        assertEquals(PermissionReason.SUBJECT_DEPARTMENT_MISSING, missingSubjectDepartment.reason());
        assertEquals(PermissionReason.RESOURCE_DEPARTMENT_MISSING, missingResourceDepartment.reason());
        assertEquals(PermissionReason.SUBJECT_DEPARTMENT_MISSING, executiveMissingSubjectDepartment.reason());
        assertEquals(PermissionReason.RESOURCE_DEPARTMENT_MISSING, executiveMissingResourceDepartment.reason());
    }

    private void assertAllowed(
            KnowledgeRole role,
            KnowledgeClassification classification,
            DeclaredAccessScope scope,
            String subjectDepartment,
            String resourceDepartment) {
        assertTrue(evaluate(
                        subject(role, subjectDepartment, true),
                        resource(classification, scope, resourceDepartment),
                        AccessGate.ALLOW,
                        AccessGate.ALLOW)
                .allowed());
    }

    private PermissionDecision evaluate(
            KnowledgeSubject subject,
            KnowledgeResource resource,
            AccessGate sourceGate,
            AccessGate orgMemoryGate) {
        return policy.evaluate(new KnowledgeAccessRequest(subject, resource, sourceGate, orgMemoryGate));
    }

    private static KnowledgeSubject subject(KnowledgeRole role, String department, boolean active) {
        return new KnowledgeSubject("org", "U001", department, role, active);
    }

    private static KnowledgeResource resource(
            KnowledgeClassification classification,
            DeclaredAccessScope scope,
            String department) {
        return new KnowledgeResource("org", "DOC001", department, classification, scope);
    }
}
