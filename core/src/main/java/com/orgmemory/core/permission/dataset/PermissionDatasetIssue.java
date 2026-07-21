package com.orgmemory.core.permission.dataset;

public record PermissionDatasetIssue(
        DatasetIssueSeverity severity,
        DatasetIssueCode code,
        String location,
        String message) {
}
