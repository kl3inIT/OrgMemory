package com.orgmemory.worker.authorization;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.Assert;

@ConfigurationProperties("orgmemory.authorization.convergence")
public record KnowledgeAuthorizationConvergenceProperties(
        Integer modelBatchSize,
        Integer tuplePageSize,
        Integer maximumTuplePages) {

    public KnowledgeAuthorizationConvergenceProperties {
        modelBatchSize = modelBatchSize == null ? 50 : modelBatchSize;
        tuplePageSize = tuplePageSize == null ? 100 : tuplePageSize;
        maximumTuplePages = maximumTuplePages == null ? 1000 : maximumTuplePages;
        Assert.isTrue(
                modelBatchSize > 0 && modelBatchSize <= 50,
                "authorization convergence model batch size must be between 1 and 50");
        Assert.isTrue(
                tuplePageSize > 0 && tuplePageSize <= 100,
                "authorization convergence tuple page size must be between 1 and 100");
        Assert.isTrue(
                maximumTuplePages > 0,
                "authorization convergence maximum tuple pages must be positive");
    }
}
