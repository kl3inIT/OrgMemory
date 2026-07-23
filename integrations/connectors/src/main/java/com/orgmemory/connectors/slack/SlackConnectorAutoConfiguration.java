package com.orgmemory.connectors.slack;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.ConnectorSourceProfile;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Contributes the Slack adapter wherever the module is on the classpath.
 *
 * <p>It used to be gated on a property, because a deployment's environment was where a
 * connection was configured and an unconfigured adapter would have had nothing to point at.
 * Now the connections live in the ledger, so the gate has moved with them: the bean is always
 * present and produces nothing at all until an administrator has enabled a workspace. Consent
 * to crawl is a row somebody wrote, not a flag on a host.
 */
@AutoConfiguration
public class SlackConnectorAutoConfiguration {

    /**
     * Declaring the profile is how this adapter tells the ledger that Slack exists. Without it
     * a Slack crawl batch would be refused, because the ledger governs only what an adapter
     * has claimed.
     */
    @Bean
    ConnectorSourceProfile slackSourceProfile() {
        return SlackSourceProfile.profile();
    }

    @Bean
    ConnectorBatchSource slackConnectorBatchSource(
            ConnectorConnectionDirectory connections,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder) {
        return new SlackConnectorBatchSource(connections, objects, restClientBuilder);
    }
}
