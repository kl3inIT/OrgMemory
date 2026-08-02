package com.orgmemory.api.assetregistry;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.AssetDeliveryService;
import com.orgmemory.core.assetregistry.prompt.PromptExecutionService;
import com.orgmemory.core.assetregistry.SkillDistributionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(AssetDeliveryControllerSecurityTests.TestConfiguration.class)
class AssetDeliveryControllerSecurityTests {

    @Autowired
    AssetDeliveryController controller;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readEndpointsRejectTokensWithoutAssetsReadScope() {
        Authentication authentication = authentication("SCOPE_profile");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(
                AuthorizationDeniedException.class,
                () -> controller.search(null, null, authentication));
    }

    @Test
    void promptRenderRejectsTokensWithoutAssetsReadScope() {
        Authentication authentication = authentication("SCOPE_profile");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(
                AuthorizationDeniedException.class,
                () -> controller.renderPrompt(
                        java.util.UUID.randomUUID(),
                        java.util.UUID.randomUUID(),
                        new AssetDeliveryController.PromptVariablesRequest(
                                java.util.Map.of()),
                        authentication));
    }

    @Test
    void skillDistributionRejectsTokensWithoutAssetsReadScope() {
        Authentication authentication = authentication("SCOPE_profile");
        SecurityContextHolder.getContext().setAuthentication(authentication);
        java.util.UUID assetId = java.util.UUID.randomUUID();
        java.util.UUID releaseId = java.util.UUID.randomUUID();

        assertThrows(
                AuthorizationDeniedException.class,
                () -> controller.skillManifest(
                        assetId, releaseId, authentication));
        assertThrows(
                AuthorizationDeniedException.class,
                () -> controller.skillPackage(
                        assetId, releaseId, authentication));
    }

    private static Authentication authentication(String authority) {
        return new TestingAuthenticationToken(
                "actor", "token", new SimpleGrantedAuthority(authority));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableMethodSecurity(proxyTargetClass = true)
    static class TestConfiguration {

        @Bean
        AssetDeliveryService delivery() {
            return mock(AssetDeliveryService.class);
        }

        @Bean
        PromptExecutionService prompts() {
            return mock(PromptExecutionService.class);
        }

        @Bean
        SkillDistributionService skills() {
            return mock(SkillDistributionService.class);
        }

        @Bean
        CurrentActorProvider actors() {
            return mock(CurrentActorProvider.class);
        }

        @Bean
        AssetDeliveryController controller(
                AssetDeliveryService delivery,
                PromptExecutionService prompts,
                SkillDistributionService skills,
                CurrentActorProvider actors) {
            return new AssetDeliveryController(
                    delivery, prompts, skills, actors);
        }
    }
}
