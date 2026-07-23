package com.orgmemory.worker.graph;

import com.orgmemory.core.ai.AiRoute;
import com.orgmemory.graphrag.port.EntityRelationExtractor;

@FunctionalInterface
interface GraphExtractorFactory {

    EntityRelationExtractor create(AiRoute route);
}
