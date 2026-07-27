package com.orgmemory.connectors.github;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.ConnectorCredentialProbe;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.ConnectorScopeBrowser;
import com.orgmemory.core.knowledge.ConnectorSourceProfile;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Proves the GitHub package contributes the complete generic connector surface. */
class GitHubConnectorAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GitHubConnectorAutoConfiguration.class))
            .withUserConfiguration(CollaboratorConfiguration.class);

    @Test
    void contributesProfileProbeScopesAndBatchSource() {
        runner.run(context -> {
            assertEquals(1, context.getBeansOfType(ConnectorSourceProfile.class).size());
            assertEquals(1, context.getBeansOfType(ConnectorCredentialProbe.class).size());
            assertEquals(1, context.getBeansOfType(ConnectorScopeBrowser.class).size());
            assertEquals(1, context.getBeansOfType(ConnectorBatchSource.class).size());
        });
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
    }
}
