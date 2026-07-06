package com.orgmemory.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Dev-only escape hatch: `--spring.profiles.active=local` disables auth entirely
 * so the API can be exercised without Keycloak. Never enable in a deployed environment.
 */
@Configuration
@EnableWebSecurity
@Profile("local")
class LocalSecurityConfig {

    @Bean
    SecurityFilterChain permitAllFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        return http.build();
    }
}
