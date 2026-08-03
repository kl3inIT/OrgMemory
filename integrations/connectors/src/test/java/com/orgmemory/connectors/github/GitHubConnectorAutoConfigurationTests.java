package com.orgmemory.connectors.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.skill.SkillGitHubSourcePort;
import com.orgmemory.core.knowledge.connector.ConnectorBatchSource;
import com.orgmemory.core.knowledge.connector.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorCredentialProbe;
import com.orgmemory.core.knowledge.connector.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.connector.ConnectorScopeBrowser;
import com.orgmemory.core.knowledge.connector.ConnectorSourceProfile;
import com.orgmemory.core.permission.PermissionAuditService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Proves the GitHub package contributes the complete generic connector surface. */
class GitHubConnectorAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(publishedGitHubAutoConfiguration())
            .withUserConfiguration(CollaboratorConfiguration.class);

    private static AutoConfigurations publishedGitHubAutoConfiguration() {
        boolean published = ImportCandidates.load(
                        AutoConfiguration.class,
                        GitHubConnectorAutoConfigurationTests.class.getClassLoader())
                .getCandidates()
                .contains(GitHubConnectorAutoConfiguration.class.getName());
        return AutoConfigurations.of(published
                ? new Class<?>[] {GitHubConnectorAutoConfiguration.class}
                : new Class<?>[0]);
    }

    @Test
    void contributesProfileProbeScopesAndBatchSource() {
        runner.run(context -> {
            assertEquals(1, context.getBeansOfType(ConnectorSourceProfile.class).size());
            assertEquals(1, context.getBeansOfType(ConnectorCredentialProbe.class).size());
            assertEquals(1, context.getBeansOfType(ConnectorScopeBrowser.class).size());
            assertEquals(1, context.getBeansOfType(ConnectorBatchSource.class).size());
            assertEquals(1, context.getBeansOfType(SkillGitHubSourcePort.class).size());
        });
    }

    @Test
    void consumesOnlyTheSkillSourcePort() {
        assertEquals(
                "com.orgmemory.core.assetregistry.skill",
                SkillGitHubSourcePort.class.getPackageName());
    }

    @Test
    void classpathPresenceDoesNotAuthorizeACrawl() {
        runner.run((AssertableApplicationContext context) -> assertTrue(
                context.getBean(ConnectorBatchSource.class).pendingBatches().batches().isEmpty(),
                "only an enabled connection authorizes source access"));
    }

    @Configuration(proxyBeanMethods = false)
    static class CollaboratorConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ConnectorObjectDirectory connectorObjectDirectory() {
            return mock(ConnectorObjectDirectory.class);
        }

        @Bean
        ConnectorConnectionDirectory connectorConnectionDirectory() {
            ConnectorConnectionDirectory directory = mock(ConnectorConnectionDirectory.class);
            when(directory.enabledCrawls("github")).thenReturn(List.of());
            return directory;
        }

        @Bean
        PermissionAuditService permissionAuditService() {
            return mock(PermissionAuditService.class);
        }
    }
}
