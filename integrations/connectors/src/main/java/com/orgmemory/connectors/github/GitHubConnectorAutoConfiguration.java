package com.orgmemory.connectors.github;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.ConnectorCredentialProbe;
import com.orgmemory.core.knowledge.ConnectorObjectDirectory;
import com.orgmemory.core.knowledge.ConnectorScopeBrowser;
import com.orgmemory.core.knowledge.ConnectorSourceProfile;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/** Contributes the GitHub adapter when the connector module is present. */
@AutoConfiguration
public class GitHubConnectorAutoConfiguration {

    @Bean
    ConnectorSourceProfile gitHubSourceProfile() {
        return GitHubSourceProfile.profile();
    }

    @Bean
    ConnectorBatchSource gitHubConnectorBatchSource(
            ConnectorConnectionDirectory connections,
            ConnectorObjectDirectory objects,
            RestClient.Builder restClientBuilder) {
        return new GitHubConnectorBatchSource(connections, objects, restClientBuilder);
    }

    @Bean
    ConnectorCredentialProbe gitHubCredentialProbe(RestClient.Builder restClientBuilder) {
        return new GitHubCredentialProbe(restClientBuilder);
    }

    @Bean
    ConnectorScopeBrowser gitHubScopeBrowser(RestClient.Builder restClientBuilder) {
        return new GitHubScopeBrowser(restClientBuilder);
    }
}

