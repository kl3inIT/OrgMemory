package com.orgmemory.integrations.authorization.openfga;

import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.authorization.RelationshipTupleReconciliationPort;
import com.orgmemory.core.authorization.RelationshipTupleWritePort;
import dev.openfga.sdk.api.client.OpenFgaClient;
import dev.openfga.sdk.api.configuration.ClientConfiguration;
import dev.openfga.sdk.errors.FgaInvalidParameterException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(OpenFgaAuthorizationProperties.class)
@ConditionalOnProperty(
        prefix = "orgmemory.authorization.openfga",
        name = {"api-url", "store-id", "authorization-model-id"})
public class OpenFgaAuthorizationConfiguration {

    @Bean
    OpenFgaClient openFgaClient(OpenFgaAuthorizationProperties properties)
            throws FgaInvalidParameterException {
        var configuration = new ClientConfiguration()
                .apiUrl(properties.apiUrl().toString())
                .storeId(properties.storeId())
                .authorizationModelId(properties.authorizationModelId())
                .maxRetries(properties.maxRetries())
                .minimumRetryDelay(properties.minimumRetryDelay());
        return new OpenFgaClient(configuration);
    }

    @Bean
    RelationshipAuthorizationPort relationshipAuthorizationPort(
            OpenFgaClient client, OpenFgaAuthorizationProperties properties) {
        return new OpenFgaRelationshipAuthorizationAdapter(
                client, properties.authorizationModelId(), properties.requestTimeout());
    }

    @Bean
    RelationshipAuthorizationSetPort relationshipAuthorizationSetPort(
            OpenFgaClient client, OpenFgaAuthorizationProperties properties) {
        return new OpenFgaRelationshipAuthorizationSetAdapter(
                client, properties.authorizationModelId(), properties.requestTimeout());
    }

    @Bean
    RelationshipTupleWritePort relationshipTupleWritePort(
            OpenFgaClient client, OpenFgaAuthorizationProperties properties) {
        return new OpenFgaRelationshipTupleWriteAdapter(
                client, properties.authorizationModelId(), properties.requestTimeout());
    }

    @Bean
    RelationshipTupleReconciliationPort relationshipTupleReconciliationPort(
            OpenFgaClient client, OpenFgaAuthorizationProperties properties) {
        return new OpenFgaRelationshipTupleReconciliationAdapter(
                client, properties.authorizationModelId(), properties.requestTimeout());
    }
}
