package com.orgmemory.graphrag.query;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;

/** Provider boundary for the LightRAG high/low keyword extraction call. */
public interface KeywordPlanningModel {

    ProcessingComponentRef component();

    KeywordPlan complete(String prompt);
}
