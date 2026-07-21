package com.orgmemory.core.permission;

import org.springframework.stereotype.Service;

@Service
public class KnowledgePermissionPolicy {

    public static final String POLICY_VERSION = "knowledge-v1";

    public PermissionDecision evaluate(KnowledgeAccessRequest request) {
        if (request == null || request.subject() == null || request.resource() == null) {
            return PermissionDecision.deny(PermissionReason.INVALID_REQUEST);
        }

        KnowledgeSubject subject = request.subject();
        KnowledgeResource resource = request.resource();
        if (isBlank(subject.organizationId())
                || isBlank(subject.subjectId())
                || isBlank(resource.organizationId())
                || isBlank(resource.resourceId())) {
            return PermissionDecision.deny(PermissionReason.INVALID_REQUEST);
        }
        if (!subject.active()) {
            return PermissionDecision.deny(PermissionReason.INACTIVE_SUBJECT);
        }
        if (!subject.organizationId().equals(resource.organizationId())) {
            return PermissionDecision.deny(PermissionReason.ORGANIZATION_MISMATCH);
        }
        if (request.sourcePermission() != AccessGate.ALLOW) {
            return PermissionDecision.deny(request.sourcePermission() == AccessGate.DENY
                    ? PermissionReason.SOURCE_PERMISSION_DENIED
                    : PermissionReason.SOURCE_PERMISSION_UNKNOWN);
        }
        if (request.orgMemoryPermission() != AccessGate.ALLOW) {
            return PermissionDecision.deny(request.orgMemoryPermission() == AccessGate.DENY
                    ? PermissionReason.ORGMEMORY_PERMISSION_DENIED
                    : PermissionReason.ORGMEMORY_PERMISSION_UNKNOWN);
        }
        if (resource.classification() == null) {
            return PermissionDecision.deny(PermissionReason.CLASSIFICATION_MISSING);
        }
        if (resource.declaredAccess() == null) {
            return PermissionDecision.deny(PermissionReason.DECLARED_ACCESS_MISSING);
        }
        if (resource.declaredAccess() != requiredScope(resource.classification())) {
            return PermissionDecision.deny(PermissionReason.DECLARED_ACCESS_MISMATCH);
        }
        if (subject.role() == null) {
            return PermissionDecision.deny(PermissionReason.SUBJECT_ROLE_MISSING);
        }

        return switch (resource.classification()) {
            case PUBLIC -> PermissionDecision.allow(PermissionReason.PUBLIC_ACCESS);
            case INTERNAL -> PermissionDecision.allow(PermissionReason.INTERNAL_ACCESS);
            case CONFIDENTIAL -> evaluateConfidential(subject, resource);
            case RESTRICTED -> subject.role() == KnowledgeRole.EXECUTIVE
                    ? PermissionDecision.allow(PermissionReason.RESTRICTED_EXECUTIVE)
                    : PermissionDecision.deny(PermissionReason.RESTRICTED_NON_EXECUTIVE);
        };
    }

    public DeclaredAccessScope requiredScope(KnowledgeClassification classification) {
        return switch (classification) {
            case PUBLIC -> DeclaredAccessScope.ALL;
            case INTERNAL -> DeclaredAccessScope.ALL_EMPLOYEES;
            case CONFIDENTIAL -> DeclaredAccessScope.OWN_DEPARTMENT;
            case RESTRICTED -> DeclaredAccessScope.EXECUTIVE_ONLY;
        };
    }

    private PermissionDecision evaluateConfidential(KnowledgeSubject subject, KnowledgeResource resource) {
        if (isBlank(subject.departmentId())) {
            return PermissionDecision.deny(PermissionReason.SUBJECT_DEPARTMENT_MISSING);
        }
        if (isBlank(resource.departmentId())) {
            return PermissionDecision.deny(PermissionReason.RESOURCE_DEPARTMENT_MISSING);
        }
        if (subject.role() == KnowledgeRole.EXECUTIVE) {
            return PermissionDecision.allow(PermissionReason.CONFIDENTIAL_EXECUTIVE);
        }
        return subject.departmentId().equals(resource.departmentId())
                ? PermissionDecision.allow(PermissionReason.CONFIDENTIAL_SAME_DEPARTMENT)
                : PermissionDecision.deny(PermissionReason.CONFIDENTIAL_OTHER_DEPARTMENT);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
