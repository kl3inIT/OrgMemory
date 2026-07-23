package com.orgmemory.connectors.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The adapter must appear exactly where it was asked for and nowhere else. Having this module
 * on the classpath is not a request to crawl anybody's workspace, and a half-configured
 * connection is not either.
 */
class SlackConnectorAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SlackConnectorAutoConfiguration.class))
            .withUserConfiguration(RestClientConfiguration.class);

    @Test
    void contributesNothingUntilTheConnectionIsEnabled() {
        runner.run(context -> assertTrue(
                context.getBeansOfType(ConnectorBatchSource.class).isEmpty(),
                "classpath presence is not consent to crawl"));
    }

    @Test
    void contributesTheAdapterWhenEnabled() {
        runner.withPropertyValues(
                        "orgmemory.connector.slack.enabled=true",
                        "orgmemory.connector.slack.connection-key=T-workspace",
                        "orgmemory.connector.slack.bot-token=xoxb-not-a-real-token",
                        "orgmemory.connector.slack.organization-id=aa000000-0000-4000-8000-000000000001",
                        "orgmemory.connector.slack.knowledge-space-id=aa000000-0000-4000-8000-000000000002",
                        "orgmemory.connector.slack.actor-user-id=aa000000-0000-4000-8000-000000000003")
                .run(context -> {
                    assertEquals(1, context.getBeansOfType(ConnectorBatchSource.class).size());
                    assertTrue(context.getBean(SlackConnectorProperties.class).isRunnable());
                });
    }

    @Test
    void staysInertWhenEnabledButNotFullyConfigured() {
        runner.withPropertyValues(
                        "orgmemory.connector.slack.enabled=true",
                        "orgmemory.connector.slack.connection-key=T-workspace")
                .run((AssertableApplicationContext context) -> {
                    assertTrue(
                            context.getBean(ConnectorBatchSource.class).pendingBatches().isEmpty(),
                            "a connection missing its token or its target contacts Slack not at all");
                });
    }

    /** The collaborators the adapter expects an application to already have. */
    @Configuration(proxyBeanMethods = false)
    static class RestClientConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ConnectorObjectDirectory connectorObjectDirectory() {
            return org.mockito.Mockito.mock(ConnectorObjectDirectory.class);
        }
    }
}
