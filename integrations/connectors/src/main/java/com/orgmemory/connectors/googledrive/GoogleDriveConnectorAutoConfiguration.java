package com.orgmemory.connectors.googledrive;

import com.orgmemory.core.knowledge.ConnectorBatchSource;
import com.orgmemory.core.knowledge.ConnectorConnectionDirectory;
import com.orgmemory.core.knowledge.ConnectorCredentialProbe;
import com.orgmemory.core.knowledge.ConnectorSourceProfile;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

/**
 * Contributes the Google Drive adapter wherever the module is on the classpath.
 *
 * <p>Three beans, and they are the whole of what a source has to declare: what it is, how to
 * crawl it, and how to check its credential. Nothing in {@code core}, in the API, or in the
 * worker names this source; no migration made room for it; no endpoint was added for it. That
 * was the claim the previous increment left untested, and this file is where it is either true
 * or it is not.
 *
 * <p>Like Slack's, the beans are always present and produce nothing at all until an
 * administrator has enabled a connection. Consent to crawl is a row somebody wrote, not a flag
 * on a host.
 */
@AutoConfiguration
public class GoogleDriveConnectorAutoConfiguration {

    @Bean
    ConnectorSourceProfile googleDriveSourceProfile() {
        return GoogleDriveSourceProfile.profile();
    }

    @Bean
    ConnectorBatchSource googleDriveConnectorBatchSource(
            ConnectorConnectionDirectory connections, RestClient.Builder restClientBuilder) {
        return new GoogleDriveConnectorBatchSource(connections, restClientBuilder);
    }

    @Bean
    ConnectorCredentialProbe googleDriveCredentialProbe(RestClient.Builder restClientBuilder) {
        return new GoogleDriveCredentialProbe(restClientBuilder);
    }
}
