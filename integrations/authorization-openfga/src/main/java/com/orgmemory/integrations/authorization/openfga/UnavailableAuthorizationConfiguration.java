package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.AuthorizationDecision;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnavailableAuthorizationConfiguration {

    @Bean
    @ConditionalOnMissingBean(RelationshipAuthorizationPort.class)
    RelationshipAuthorizationPort unavailableRelationshipAuthorizationPort() {
        return query -> AuthorizationDecision.indeterminate(
                "OPENFGA_NOT_CONFIGURED",
                "openfga-unconfigured");
    }
}
