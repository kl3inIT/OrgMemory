package com.orgmemory.api.admin;

import com.orgmemory.connectors.slack.SlackCredentialProbe;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The API talks to a source in exactly one place: checking a credential an administrator just
 * typed. The connector module auto-configures the crawler only where a deployment asked for one,
 * which is the worker; the probe is declared here instead so that reaching Slack from the API
 * stays a listed decision rather than something the classpath arranged.
 */
@Configuration(proxyBeanMethods = false)
class AdminConnectorConfiguration {

    @Bean
    SlackCredentialProbe slackCredentialProbe(RestClient.Builder restClientBuilder) {
        return new SlackCredentialProbe(restClientBuilder);
    }
}
