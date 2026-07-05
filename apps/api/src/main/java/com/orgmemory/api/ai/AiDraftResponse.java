package com.orgmemory.api.ai;

import com.orgmemory.core.capability.AssetType;
import com.orgmemory.core.capability.RiskLevel;

record AiDraftResponse(
        boolean aiEnabled,
        String source,
        String note,
        String title,
        String summary,
        AssetType assetType,
        String useCase,
        String businessProcess,
        String aiTool,
        String tagNames,
        RiskLevel riskLevel,
        String promptTemplate,
        String workflowStepsJson,
        String inputSchemaJson,
        String outputSchemaJson,
        String exampleInput,
        String exampleOutput) {
}
