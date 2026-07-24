package com.orgmemory.core.knowledge;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("orgmemory.graph-rag.explorer")
public record GraphExplorerProperties(
        int defaultEntityLimit,
        int maximumEntityLimit,
        int maximumRelationLimit,
        int maximumQueryLength) {

    public GraphExplorerProperties {
        if (defaultEntityLimit < 1
                || maximumEntityLimit < defaultEntityLimit
                || maximumRelationLimit < 1
                || maximumQueryLength < 1) {
            throw new IllegalArgumentException(
                    "Graph explorer limits must be positive and internally consistent");
        }
    }

    public static GraphExplorerProperties defaults() {
        return new GraphExplorerProperties(60, 200, 400, 200);
    }
}
