package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RelationshipTupleWriteResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnavailableAuthorizationConfiguration {

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
}
