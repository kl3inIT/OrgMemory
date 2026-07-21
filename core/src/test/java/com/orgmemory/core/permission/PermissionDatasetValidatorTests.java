package com.orgmemory.core.permission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.permission.dataset.DatasetEvaluation;
import com.orgmemory.core.permission.dataset.DatasetIssueCode;
import com.orgmemory.core.permission.dataset.DatasetPermissionRule;
import com.orgmemory.core.permission.dataset.PermissionDataset;
import com.orgmemory.core.permission.dataset.PermissionDatasetValidationReport;
import com.orgmemory.core.permission.dataset.PermissionDatasetValidator;
import com.orgmemory.core.permission.dataset.PermissionMatrixEntry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PermissionDatasetValidatorTests {

    private final PermissionDatasetValidator validator = new PermissionDatasetValidator(
            new KnowledgePermissionPolicy());

    @Test
    void reportsBenchmarkExpectationMismatchWithoutSpecialCasingIt() {
        KnowledgeSubject financeManager = new KnowledgeSubject(
                "dataset", "U002", "FIN", KnowledgeRole.MANAGER, true);
        KnowledgeResource financeInternal = new KnowledgeResource(
                "dataset", "DOC001", "FIN", KnowledgeClassification.INTERNAL, DeclaredAccessScope.ALL_EMPLOYEES);
        KnowledgeResource operationsInternal = new KnowledgeResource(
                "dataset", "DOC030", "OPS", KnowledgeClassification.INTERNAL, DeclaredAccessScope.ALL_EMPLOYEES);
        PermissionDataset dataset = new PermissionDataset(
                List.of("DOC001", "DOC030"),
                List.of("FIN", "OPS"),
                List.of(KnowledgeRole.values()),
                List.of(financeInternal, operationsInternal),
                List.of(financeManager),
                canonicalMatrix(),
                List.of(
                        new DatasetEvaluation("P001", "U002", List.of("DOC001"), AccessOutcome.ALLOW),
                        new DatasetEvaluation("P035", "U002", List.of("DOC030"), AccessOutcome.DENY)));

        PermissionDatasetValidationReport report = validator.validate(dataset);

        assertFalse(report.valid());
        assertEquals(2, report.evaluatedCases());
        assertEquals(1, report.matchedExpectations());
        assertEquals(1, report.errorCount());
        assertEquals(DatasetIssueCode.EVALUATION_EXPECTATION_MISMATCH, report.issues().getFirst().code());
        assertEquals("evaluation:P035", report.issues().getFirst().location());
        assertTrue(report.issues().getFirst().message().contains("DOC030=ALLOW(INTERNAL_ACCESS)"));
    }

    @Test
    void allDocumentsInMultiDocumentEvaluationMustBeAllowed() {
        PermissionDataset dataset = new PermissionDataset(
                List.of("DOC001", "DOC040"),
                List.of("PROD", "EXEC"),
                List.of(KnowledgeRole.values()),
                List.of(
                        new KnowledgeResource(
                                "dataset", "DOC001", "PROD", KnowledgeClassification.PUBLIC, DeclaredAccessScope.ALL),
                        new KnowledgeResource(
                                "dataset",
                                "DOC040",
                                "EXEC",
                                KnowledgeClassification.RESTRICTED,
                                DeclaredAccessScope.EXECUTIVE_ONLY)),
                List.of(new KnowledgeSubject(
                        "dataset", "U003", "PROD", KnowledgeRole.EMPLOYEE, true)),
                canonicalMatrix(),
                List.of(new DatasetEvaluation(
                        "P031", "U003", List.of("DOC001", "DOC040"), AccessOutcome.DENY)));

        PermissionDatasetValidationReport report = validator.validate(dataset);

        assertTrue(report.valid());
        assertEquals(1, report.evaluatedCases());
        assertEquals(1, report.matchedExpectations());
    }

    @Test
    void missingSecurityMetadataAndExpectedOutcomeAreValidationErrors() {
        PermissionDataset dataset = new PermissionDataset(
                List.of("DOC001"),
                List.of("FIN"),
                List.of(KnowledgeRole.values()),
                List.of(new KnowledgeResource("dataset", "DOC001", null, null, null)),
                List.of(new KnowledgeSubject("dataset", "U001", null, null, true)),
                canonicalMatrix(),
                List.of(new DatasetEvaluation("P001", "U001", List.of("DOC001"), null)));

        PermissionDatasetValidationReport report = validator.validate(dataset);
        var issueCodes = report.issues().stream().map(issue -> issue.code()).toList();

        assertFalse(report.valid());
        assertTrue(issueCodes.contains(DatasetIssueCode.MISSING_RESOURCE_CLASSIFICATION));
        assertTrue(issueCodes.contains(DatasetIssueCode.MISSING_DECLARED_ACCESS));
        assertTrue(issueCodes.contains(DatasetIssueCode.MISSING_SUBJECT_ROLE));
        assertTrue(issueCodes.contains(DatasetIssueCode.MISSING_SUBJECT_DEPARTMENT));
        assertTrue(issueCodes.contains(DatasetIssueCode.MISSING_EXPECTED_OUTCOME));
    }

    private static List<PermissionMatrixEntry> canonicalMatrix() {
        List<PermissionMatrixEntry> entries = new ArrayList<>();
        for (KnowledgeClassification classification : KnowledgeClassification.values()) {
            for (KnowledgeRole role : KnowledgeRole.values()) {
                DatasetPermissionRule rule = switch (classification) {
                    case PUBLIC, INTERNAL -> DatasetPermissionRule.ALLOW;
                    case CONFIDENTIAL -> role == KnowledgeRole.EXECUTIVE
                            ? DatasetPermissionRule.ALLOW
                            : DatasetPermissionRule.OWN_DEPARTMENT;
                    case RESTRICTED -> role == KnowledgeRole.EXECUTIVE
                            ? DatasetPermissionRule.ALLOW
                            : DatasetPermissionRule.DENY;
                };
                entries.add(new PermissionMatrixEntry(classification, role, rule));
            }
        }
        return entries;
    }
}
