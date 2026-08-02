package com.orgmemory.core.knowledge.retrieval;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Registers the canonical query engine for interactive adapters that opt into it. */
@Configuration(proxyBeanMethods = false)
@Import(DefaultCanonicalHybridKnowledgeSearch.class)
public class CanonicalHybridKnowledgeSearchConfiguration {
}
