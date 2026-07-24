package com.orgmemory.integrations.graphrag.springai;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

record StructuredKeywordPlanResponse(
        @JsonProperty("high_level_keywords") List<String> highLevelKeywords,
        @JsonProperty("low_level_keywords") List<String> lowLevelKeywords) {
}
