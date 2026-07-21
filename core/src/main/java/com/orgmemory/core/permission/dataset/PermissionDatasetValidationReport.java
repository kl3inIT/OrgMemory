package com.orgmemory.core.permission.dataset;

import java.util.List;

public record PermissionDatasetValidationReport(
        int documentCount,
        int metadataCount,
        int userCount,
        int evaluationCount,
        int evaluatedCases,
        int matchedExpectations,
        List<PermissionDatasetIssue> issues) {

    public PermissionDatasetValidationReport {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    public long errorCount() {
        return issues.stream().filter(issue -> issue.severity() == DatasetIssueSeverity.ERROR).count();
    }

    public boolean valid() {
        return errorCount() == 0;
    }
}
