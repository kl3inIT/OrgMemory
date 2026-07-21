package com.orgmemory.core.permission.dataset;

import com.orgmemory.core.permission.AccessGate;
import com.orgmemory.core.permission.AccessOutcome;
import com.orgmemory.core.permission.DeclaredAccessScope;
import com.orgmemory.core.permission.KnowledgeAccessRequest;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.permission.KnowledgePermissionPolicy;
import com.orgmemory.core.permission.KnowledgeResource;
import com.orgmemory.core.permission.KnowledgeRole;
import com.orgmemory.core.permission.KnowledgeSubject;
import com.orgmemory.core.permission.PermissionDecision;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class PermissionDatasetValidator {

    private final KnowledgePermissionPolicy policy;

    public PermissionDatasetValidator(KnowledgePermissionPolicy policy) {
        this.policy = policy;
    }

    public PermissionDatasetValidationReport validate(PermissionDataset dataset) {
        List<PermissionDatasetIssue> issues = new ArrayList<>();
        if (dataset == null) {
            issues.add(error(DatasetIssueCode.MISSING_IDENTIFIER, "dataset", "Dataset is missing."));
            return new PermissionDatasetValidationReport(0, 0, 0, 0, 0, 0, issues);
        }

        Set<String> documentIds = uniqueStrings(
                dataset.documentIds(), DatasetIssueCode.DUPLICATE_DOCUMENT_ID, "document", issues);
        Set<String> departments = uniqueStrings(
                dataset.departments(), DatasetIssueCode.DUPLICATE_DEPARTMENT, "department", issues);
        Map<String, KnowledgeResource> resources = uniqueResources(dataset.resources(), issues);
        Map<String, KnowledgeSubject> subjects = uniqueSubjects(dataset.subjects(), issues);
        validateDeclaredRoles(dataset.declaredRoles(), issues);
        validateDocumentMetadata(documentIds, resources, departments, issues);
        validateSubjects(subjects.values(), departments, issues);
        validatePermissionMatrix(dataset.permissionMatrix(), issues);
        validateEvaluationIds(dataset.evaluations(), issues);

        int evaluatedCases = 0;
        int matchedExpectations = 0;
        for (DatasetEvaluation evaluation : dataset.evaluations()) {
            if (evaluation == null || isBlank(evaluation.evaluationId())) {
                issues.add(error(DatasetIssueCode.MISSING_IDENTIFIER, "evaluation", "Evaluation ID is missing."));
                continue;
            }
            KnowledgeSubject subject = subjects.get(evaluation.subjectId());
            if (subject == null) {
                issues.add(error(
                        DatasetIssueCode.UNKNOWN_EVALUATION_USER,
                        "evaluation:" + evaluation.evaluationId(),
                        "Unknown user " + evaluation.subjectId() + "."));
            }
            List<KnowledgeResource> evaluationResources = new ArrayList<>();
            if (evaluation.resourceIds().isEmpty()) {
                issues.add(error(
                        DatasetIssueCode.UNKNOWN_EVALUATION_DOCUMENT,
                        "evaluation:" + evaluation.evaluationId(),
                        "Evaluation has no expected document."));
            }
            if (evaluation.expectedOutcome() == null) {
                issues.add(error(
                        DatasetIssueCode.MISSING_EXPECTED_OUTCOME,
                        "evaluation:" + evaluation.evaluationId(),
                        "Expected permission outcome is missing."));
            }
            for (String resourceId : evaluation.resourceIds()) {
                KnowledgeResource resource = resources.get(resourceId);
                if (resource == null) {
                    issues.add(error(
                            DatasetIssueCode.UNKNOWN_EVALUATION_DOCUMENT,
                            "evaluation:" + evaluation.evaluationId(),
                            "Unknown document " + resourceId + "."));
                } else {
                    evaluationResources.add(resource);
                }
            }
            if (subject == null
                    || evaluationResources.size() != evaluation.resourceIds().size()
                    || evaluation.expectedOutcome() == null) {
                continue;
            }

            List<PermissionDecision> decisions = evaluationResources.stream()
                    .map(resource -> policy.evaluate(new KnowledgeAccessRequest(
                            subject, resource, AccessGate.ALLOW, AccessGate.ALLOW)))
                    .toList();
            AccessOutcome actualOutcome = decisions.stream().allMatch(PermissionDecision::allowed)
                    ? AccessOutcome.ALLOW
                    : AccessOutcome.DENY;
            evaluatedCases++;
            if (actualOutcome == evaluation.expectedOutcome()) {
                matchedExpectations++;
            } else {
                String actualDetails = evaluationResources.stream()
                        .map(resource -> {
                            PermissionDecision decision = policy.evaluate(new KnowledgeAccessRequest(
                                    subject, resource, AccessGate.ALLOW, AccessGate.ALLOW));
                            return "%s=%s(%s)".formatted(resource.resourceId(), decision.outcome(), decision.reason());
                        })
                        .reduce((left, right) -> left + ", " + right)
                        .orElse("no decisions");
                issues.add(error(
                        DatasetIssueCode.EVALUATION_EXPECTATION_MISMATCH,
                        "evaluation:" + evaluation.evaluationId(),
                        ("Expected %s but policy returned %s: user=%s role=%s department=%s; %s.")
                                .formatted(
                                        evaluation.expectedOutcome(),
                                        actualOutcome,
                                        subject.subjectId(),
                                        subject.role(),
                                        subject.departmentId(),
                                        actualDetails)));
            }
        }

        return new PermissionDatasetValidationReport(
                dataset.documentIds().size(),
                dataset.resources().size(),
                dataset.subjects().size(),
                dataset.evaluations().size(),
                evaluatedCases,
                matchedExpectations,
                issues);
    }

    private void validateDocumentMetadata(
            Set<String> documentIds,
            Map<String, KnowledgeResource> resources,
            Set<String> departments,
            List<PermissionDatasetIssue> issues) {
        for (String documentId : documentIds) {
            if (!resources.containsKey(documentId)) {
                issues.add(error(
                        DatasetIssueCode.MISSING_DOCUMENT_METADATA,
                        "document:" + documentId,
                        "Document has no metadata row."));
            }
        }
        for (KnowledgeResource resource : resources.values()) {
            if (!documentIds.contains(resource.resourceId())) {
                issues.add(error(
                        DatasetIssueCode.ORPHAN_DOCUMENT_METADATA,
                        "document:" + resource.resourceId(),
                        "Metadata row has no matching document."));
            }
            if (!isBlank(resource.departmentId()) && !departments.contains(resource.departmentId())) {
                issues.add(error(
                        DatasetIssueCode.UNKNOWN_RESOURCE_DEPARTMENT,
                        "document:" + resource.resourceId(),
                        "Unknown department " + resource.departmentId() + "."));
            }
            if (resource.classification() == null) {
                issues.add(error(
                        DatasetIssueCode.MISSING_RESOURCE_CLASSIFICATION,
                        "document:" + resource.resourceId(),
                        "Classification is missing."));
            }
            if (resource.declaredAccess() == null) {
                issues.add(error(
                        DatasetIssueCode.MISSING_DECLARED_ACCESS,
                        "document:" + resource.resourceId(),
                        "Declared access is missing."));
            }
            if (resource.classification() == KnowledgeClassification.CONFIDENTIAL
                    && isBlank(resource.departmentId())) {
                issues.add(error(
                        DatasetIssueCode.MISSING_RESOURCE_DEPARTMENT,
                        "document:" + resource.resourceId(),
                        "Confidential document department is missing."));
            }
            if (resource.classification() != null && resource.declaredAccess() != null) {
                DeclaredAccessScope expected = policy.requiredScope(resource.classification());
                if (resource.declaredAccess() != expected) {
                    issues.add(error(
                            DatasetIssueCode.DECLARED_ACCESS_MISMATCH,
                            "document:" + resource.resourceId(),
                            "Classification %s requires %s, found %s."
                                    .formatted(resource.classification(), expected, resource.declaredAccess())));
                }
            }
        }
    }

    private static void validateSubjects(
            Iterable<KnowledgeSubject> subjects,
            Set<String> departments,
            List<PermissionDatasetIssue> issues) {
        for (KnowledgeSubject subject : subjects) {
            if (subject.role() == null) {
                issues.add(error(
                        DatasetIssueCode.MISSING_SUBJECT_ROLE,
                        "user:" + subject.subjectId(),
                        "Knowledge role is missing."));
            }
            if (isBlank(subject.departmentId())) {
                issues.add(error(
                        DatasetIssueCode.MISSING_SUBJECT_DEPARTMENT,
                        "user:" + subject.subjectId(),
                        "User department is missing."));
            }
            if (!isBlank(subject.departmentId()) && !departments.contains(subject.departmentId())) {
                issues.add(error(
                        DatasetIssueCode.UNKNOWN_USER_DEPARTMENT,
                        "user:" + subject.subjectId(),
                        "Unknown department " + subject.departmentId() + "."));
            }
        }
    }

    private static void validateDeclaredRoles(
            List<KnowledgeRole> declaredRoles,
            List<PermissionDatasetIssue> issues) {
        Set<KnowledgeRole> roles = EnumSet.noneOf(KnowledgeRole.class);
        for (KnowledgeRole role : declaredRoles) {
            if (role == null) {
                continue;
            }
            if (!roles.add(role)) {
                issues.add(error(DatasetIssueCode.DUPLICATE_ROLE, "role:" + role, "Role is duplicated."));
            }
        }
        for (KnowledgeRole required : KnowledgeRole.values()) {
            if (!roles.contains(required)) {
                issues.add(error(
                        DatasetIssueCode.MISSING_REQUIRED_ROLE,
                        "role:" + required,
                        "Required role is missing."));
            }
        }
    }

    private static void validatePermissionMatrix(
            List<PermissionMatrixEntry> entries,
            List<PermissionDatasetIssue> issues) {
        Map<String, PermissionMatrixEntry> matrix = new HashMap<>();
        for (PermissionMatrixEntry entry : entries) {
            if (entry == null || entry.classification() == null || entry.role() == null || entry.rule() == null) {
                issues.add(error(
                        DatasetIssueCode.MISSING_IDENTIFIER,
                        "permission-matrix",
                        "Permission matrix entry is incomplete."));
                continue;
            }
            String key = entry.classification() + ":" + entry.role();
            if (matrix.putIfAbsent(key, entry) != null) {
                issues.add(error(
                        DatasetIssueCode.DUPLICATE_PERMISSION_MATRIX_ENTRY,
                        "permission-matrix:" + key,
                        "Permission matrix entry is duplicated."));
            }
        }
        for (KnowledgeClassification classification : KnowledgeClassification.values()) {
            for (KnowledgeRole role : KnowledgeRole.values()) {
                String key = classification + ":" + role;
                PermissionMatrixEntry entry = matrix.get(key);
                if (entry == null) {
                    issues.add(error(
                            DatasetIssueCode.MISSING_PERMISSION_MATRIX_ENTRY,
                            "permission-matrix:" + key,
                            "Permission matrix entry is missing."));
                    continue;
                }
                DatasetPermissionRule expected = expectedRule(classification, role);
                if (entry.rule() != expected) {
                    issues.add(error(
                            DatasetIssueCode.PERMISSION_MATRIX_MISMATCH,
                            "permission-matrix:" + key,
                            "Expected %s, found %s.".formatted(expected, entry.rule())));
                }
            }
        }
    }

    private static DatasetPermissionRule expectedRule(
            KnowledgeClassification classification,
            KnowledgeRole role) {
        return switch (classification) {
            case PUBLIC, INTERNAL -> DatasetPermissionRule.ALLOW;
            case CONFIDENTIAL -> role == KnowledgeRole.EXECUTIVE
                    ? DatasetPermissionRule.ALLOW
                    : DatasetPermissionRule.OWN_DEPARTMENT;
            case RESTRICTED -> role == KnowledgeRole.EXECUTIVE
                    ? DatasetPermissionRule.ALLOW
                    : DatasetPermissionRule.DENY;
        };
    }

    private static Set<String> uniqueStrings(
            List<String> values,
            DatasetIssueCode duplicateCode,
            String locationPrefix,
            List<PermissionDatasetIssue> issues) {
        Set<String> result = new HashSet<>();
        for (String value : values) {
            if (isBlank(value)) {
                issues.add(error(
                        DatasetIssueCode.MISSING_IDENTIFIER,
                        locationPrefix,
                        "Identifier is missing."));
            } else if (!result.add(value)) {
                issues.add(error(
                        duplicateCode,
                        locationPrefix + ":" + value,
                        "Identifier is duplicated."));
            }
        }
        return result;
    }

    private static Map<String, KnowledgeResource> uniqueResources(
            List<KnowledgeResource> resources,
            List<PermissionDatasetIssue> issues) {
        Map<String, KnowledgeResource> result = new HashMap<>();
        for (KnowledgeResource resource : resources) {
            if (resource == null || isBlank(resource.resourceId())) {
                issues.add(error(DatasetIssueCode.MISSING_IDENTIFIER, "document-metadata", "Document ID is missing."));
            } else if (result.putIfAbsent(resource.resourceId(), resource) != null) {
                issues.add(error(
                        DatasetIssueCode.DUPLICATE_METADATA_DOCUMENT_ID,
                        "document:" + resource.resourceId(),
                        "Metadata document ID is duplicated."));
            }
        }
        return result;
    }

    private static Map<String, KnowledgeSubject> uniqueSubjects(
            List<KnowledgeSubject> subjects,
            List<PermissionDatasetIssue> issues) {
        Map<String, KnowledgeSubject> result = new HashMap<>();
        for (KnowledgeSubject subject : subjects) {
            if (subject == null || isBlank(subject.subjectId())) {
                issues.add(error(DatasetIssueCode.MISSING_IDENTIFIER, "user", "User ID is missing."));
            } else if (result.putIfAbsent(subject.subjectId(), subject) != null) {
                issues.add(error(
                        DatasetIssueCode.DUPLICATE_USER_ID,
                        "user:" + subject.subjectId(),
                        "User ID is duplicated."));
            }
        }
        return result;
    }

    private static void validateEvaluationIds(
            List<DatasetEvaluation> evaluations,
            List<PermissionDatasetIssue> issues) {
        Set<String> ids = new HashSet<>();
        for (DatasetEvaluation evaluation : evaluations) {
            if (evaluation == null || isBlank(evaluation.evaluationId())) {
                continue;
            }
            if (!ids.add(evaluation.evaluationId())) {
                issues.add(error(
                        DatasetIssueCode.DUPLICATE_EVALUATION_ID,
                        "evaluation:" + evaluation.evaluationId(),
                        "Evaluation ID is duplicated."));
            }
        }
    }

    private static PermissionDatasetIssue error(
            DatasetIssueCode code,
            String location,
            String message) {
        return new PermissionDatasetIssue(DatasetIssueSeverity.ERROR, code, location, message);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
