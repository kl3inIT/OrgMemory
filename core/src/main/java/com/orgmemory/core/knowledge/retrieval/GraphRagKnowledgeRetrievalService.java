package com.orgmemory.core.knowledge.retrieval;

import com.orgmemory.core.knowledge.search.PermissionAwareKnowledgeSearch;

/** Adapter-facing GraphRAG retrieval engine, present only when its runtime is configured. */
public interface GraphRagKnowledgeRetrievalService extends PermissionAwareKnowledgeSearch {
}
