package com.orgmemory.connectors.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The adapter is present wherever the module is, and crawls nobody's workspace until a row says
 * to. Consent moved from a property on the host to a connection an administrator enabled, so
 * what this proves is that the bean existing is not itself a decision to contact Slack.
 */
class SlackConnectorAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SlackConnectorAutoConfiguration.class))
            .withUserConfiguration(CollaboratorConfiguration.class);

    @Test
    void contributesTheAdapterWhereverTheModuleIsPresent() {
        runner.run(context -> assertEquals(1, context.getBeansOfType(ConnectorBatchSource.class).size()));
    }

    @Test
    void contributesNothingToCrawlUntilAConnectionIsEnabled() {
        runner.run((AssertableApplicationContext context) -> assertTrue(
                context.getBean(ConnectorBatchSource.class).pendingBatches().isEmpty(),
                "classpath presence is not consent to crawl"));
    }

    /** The collaborators the adapter expects an application to already have. */
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
            when(directory.enabledCrawls("slack")).thenReturn(List.of());
            return directory;
        }
    }
}
