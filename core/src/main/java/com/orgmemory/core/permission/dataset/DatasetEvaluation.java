package com.orgmemory.core.permission.dataset;

import com.orgmemory.core.permission.AccessOutcome;
import java.util.List;

public record DatasetEvaluation(
        String evaluationId,
        String subjectId,
        List<String> resourceIds,
        AccessOutcome expectedOutcome) {

    public DatasetEvaluation {
        resourceIds = resourceIds == null ? List.of() : List.copyOf(resourceIds);
    }
}
