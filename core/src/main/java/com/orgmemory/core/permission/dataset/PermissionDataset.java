package com.orgmemory.core.permission.dataset;

import com.orgmemory.core.permission.KnowledgeResource;
import com.orgmemory.core.permission.KnowledgeRole;
import com.orgmemory.core.permission.KnowledgeSubject;
import java.util.List;

public record PermissionDataset(
        List<String> documentIds,
        List<String> departments,
        List<KnowledgeRole> declaredRoles,
        List<KnowledgeResource> resources,
        List<KnowledgeSubject> subjects,
        List<PermissionMatrixEntry> permissionMatrix,
        List<DatasetEvaluation> evaluations) {

    public PermissionDataset {
        documentIds = immutable(documentIds);
        departments = immutable(departments);
        declaredRoles = immutable(declaredRoles);
        resources = immutable(resources);
        subjects = immutable(subjects);
        permissionMatrix = immutable(permissionMatrix);
        evaluations = immutable(evaluations);
    }

    private static <T> List<T> immutable(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
