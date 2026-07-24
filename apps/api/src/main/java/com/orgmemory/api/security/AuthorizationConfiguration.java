package com.orgmemory.api.security;

import com.orgmemory.core.authorization.AccessExplanationService;
import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipExpansionPort;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import com.orgmemory.core.authorization.RoleAdministrationService;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuthorizationConfiguration {

    @Bean
    EffectiveAuthorizationService effectiveAuthorizationService(RelationshipAuthorizationPort relationships) {
        return new EffectiveAuthorizationService(relationships);
    }

    @Bean
    AccessExplanationService accessExplanationService(
            RelationshipAuthorizationPort relationships, RelationshipExpansionPort expansion) {
        return new AccessExplanationService(relationships, expansion, Clock.systemUTC());
    }

    @Bean
    RoleAdministrationService roleAdministrationService(
            RelationshipTupleWritePort writes, RelationshipTupleReconciliationPort tuples) {
        return new RoleAdministrationService(writes, tuples);
    }
}
