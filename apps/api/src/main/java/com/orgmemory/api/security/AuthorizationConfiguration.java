package com.orgmemory.api.security;

import com.orgmemory.core.authorization.EffectiveAuthorizationService;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class AuthorizationConfiguration {

    @Bean
    EffectiveAuthorizationService effectiveAuthorizationService(RelationshipAuthorizationPort relationships) {
        return new EffectiveAuthorizationService(relationships);
    }
}
