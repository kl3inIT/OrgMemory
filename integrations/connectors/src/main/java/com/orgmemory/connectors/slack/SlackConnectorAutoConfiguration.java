package com.orgmemory.connectors.slack;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Contributes the Slack adapter only where a deployment asked for it. An application that
 * merely has this module on its classpath gets nothing, which keeps the connector inert by
 * default in exactly the way the driver that consumes it already is.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "orgmemory.connector.slack", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SlackConnectorProperties.class)
public class SlackConnectorAutoConfiguration {

    @Bean
    ConnectorBatchSource slackConnectorBatchSource(
            SlackConnectorProperties properties,
            SlackCredentialProvider credentials,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder) {
        return new SlackConnectorBatchSource(properties, credentials, objects, restClientBuilder);
    }

    @Bean
    SlackCredentialProvider slackCredentialProvider(SlackConnectorProperties properties) {
        return new ConfiguredSlackCredentialProvider(properties);
    }
}
