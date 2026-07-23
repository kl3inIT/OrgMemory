package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.AuthorizedResourceSetResult;
import com.orgmemory.core.authorization.BatchAuthorizationResult;
import com.orgmemory.core.authorization.RelationshipTuplePage;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteRequest;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * A fail-closed stand-in for every authorization port, used when OpenFGA is not configured.
 *
 * <p>Every port needs one. A port with no fallback is not simply absent — it silently removes
 * whatever depends on it, and a component-scanned {@code @ConditionalOnBean} resolves against
 * whatever happens to have been registered by then, which is scan order rather than
 * configuration. Answering "unconfigured" is the behaviour the rest of the system already knows
 * how to read.
 */
@Configuration
public class UnavailableAuthorizationConfiguration {

    private static final String NOT_CONFIGURED = "OPENFGA_NOT_CONFIGURED";
    private static final String UNCONFIGURED = "openfga-unconfigured";

    @Bean
    @ConditionalOnMissingBean(RelationshipAuthorizationPort.class)
    RelationshipAuthorizationPort unavailableRelationshipAuthorizationPort() {
        return query -> {
            Objects.requireNonNull(query, "query");
            return AuthorizationDecision.indeterminate(
                    "OPENFGA_NOT_CONFIGURED",
                    "openfga-unconfigured");
        };
    }

    @Bean
    @ConditionalOnMissingBean(RelationshipTupleWritePort.class)
    RelationshipTupleWritePort unavailableRelationshipTupleWritePort() {
        return request -> {
            Objects.requireNonNull(request, "request");
            return RelationshipTupleWriteResult.indeterminate(
                    "OPENFGA_NOT_CONFIGURED",
                    "openfga-unconfigured");
        };
    }

    @Bean
    @ConditionalOnMissingBean(RelationshipTupleReconciliationPort.class)
    RelationshipTupleReconciliationPort unavailableRelationshipTupleReconciliationPort() {
        return new RelationshipTupleReconciliationPort() {
            @Override
            public String policyVersion() {
                return UNCONFIGURED;
            }

            @Override
            public RelationshipTuplePage read(int pageSize, String continuationToken) {
                return RelationshipTuplePage.indeterminate(NOT_CONFIGURED, UNCONFIGURED);
            }

            @Override
            public RelationshipTupleWriteResult delete(RelationshipTupleWriteRequest request) {
                Objects.requireNonNull(request, "request");
                return RelationshipTupleWriteResult.indeterminate(NOT_CONFIGURED, UNCONFIGURED);
            }
        };
    }

    @Bean
    @ConditionalOnMissingBean(RelationshipAuthorizationSetPort.class)
    RelationshipAuthorizationSetPort unavailableRelationshipAuthorizationSetPort() {
        return new RelationshipAuthorizationSetPort() {
            @Override
            public AuthorizedResourceSetResult listAuthorizedResources(
                    com.orgmemory.core.authorization.AuthorizedResourceQuery query) {
                Objects.requireNonNull(query, "query");
                return AuthorizedResourceSetResult.indeterminate(
                        "OPENFGA_NOT_CONFIGURED", "openfga-unconfigured");
            }

            @Override
            public BatchAuthorizationResult batchCheck(
                    com.orgmemory.core.authorization.BatchAuthorizationQuery query) {
                Objects.requireNonNull(query, "query");
                return BatchAuthorizationResult.indeterminate(
                        "OPENFGA_NOT_CONFIGURED", "openfga-unconfigured");
            }
        };
    }
}
