package com.orgmemory.api.security;

import com.orgmemory.core.ai.AiGatewayCapability;
import com.orgmemory.core.shared.secret.SecretCipherProperties;
import com.orgmemory.integrations.ai.openai.AiGatewayProperties;
import com.orgmemory.integrations.authorization.openfga.OpenFgaAuthorizationProperties;
import com.orgmemory.integrations.storage.minio.MinioObjectStorageProperties;
import java.net.URI;
import java.util.Objects;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

final class ProductionConfigurationGuard implements InitializingBean {

    private static final String LOCAL_DATABASE_PASSWORD = "orgmemory";
    private static final String LOCAL_OIDC_CLIENT_SECRET = "orgmemory-local-dev-only";
    private static final String LOCAL_OBJECT_STORAGE_ACCESS_KEY = "orgmemory-local";
    private static final String LOCAL_OBJECT_STORAGE_SECRET_KEY = "orgmemory-local-secret";
    private static final String LOCAL_SECRETS_KEY = "orgmemory-local-dev-only-secret-key";

    private final DataSourceProperties dataSource;
    private final OrgMemoryOidcProperties oidc;
    private final OpenFgaAuthorizationProperties openFga;
    private final MinioObjectStorageProperties objectStorage;
    private final AiGatewayProperties ai;
    private final SecretCipherProperties secrets;

    ProductionConfigurationGuard(
            DataSourceProperties dataSource,
            OrgMemoryOidcProperties oidc,
            OpenFgaAuthorizationProperties openFga,
            MinioObjectStorageProperties objectStorage,
            AiGatewayProperties ai,
            SecretCipherProperties secrets) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.oidc = Objects.requireNonNull(oidc, "oidc");
        this.openFga = Objects.requireNonNull(openFga, "openFga");
        this.objectStorage = Objects.requireNonNull(objectStorage, "objectStorage");
        this.ai = Objects.requireNonNull(ai, "ai");
        this.secrets = Objects.requireNonNull(secrets, "secrets");
    }

    @Override
    public void afterPropertiesSet() {
        requireConfigured(dataSource.getUrl(), "spring.datasource.url");
        requireConfigured(dataSource.getUsername(), "spring.datasource.username");
        requireSecret(dataSource.getPassword(), LOCAL_DATABASE_PASSWORD, "spring.datasource.password");
        requireHttps(oidc.issuerUri(), "orgmemory.security.oidc.issuer-uri");
        requireHttps(oidc.webBaseUrl(), "orgmemory.security.oidc.web-base-url");
        requireSecret(
                oidc.clientSecret(),
                LOCAL_OIDC_CLIENT_SECRET,
                "orgmemory.security.oidc.client-secret");
        requireConfigured(openFga.storeId(), "orgmemory.authorization.openfga.store-id");
        requireConfigured(
                openFga.authorizationModelId(),
                "orgmemory.authorization.openfga.authorization-model-id");
        requireSecret(
                objectStorage.accessKey(),
                LOCAL_OBJECT_STORAGE_ACCESS_KEY,
                "orgmemory.storage.object.access-key");
        requireSecret(
                objectStorage.secretKey(),
                LOCAL_OBJECT_STORAGE_SECRET_KEY,
                "orgmemory.storage.object.secret-key");

        // Stored source credentials are only as private as this key. A deployment that kept the
        // development one would encrypt every token with a value published in this repository.
        requireSecret(secrets.key(), LOCAL_SECRETS_KEY, "orgmemory.secrets.key");

        requireGateway(
                ai.routes().assistantChat(),
                AiGatewayCapability.CHAT,
                "orgmemory.ai.routes.assistant-chat");
        requireGateway(
                ai.routes().embedding(),
                AiGatewayCapability.EMBEDDING,
                "orgmemory.ai.routes.embedding");
    }

    private void requireGateway(
            AiGatewayProperties.Route route,
            AiGatewayCapability capability,
            String routeProperty) {
        requireConfigured(route.modelId(), routeProperty + ".model-id");
        AiGatewayProperties.Gateway gateway = ai.gateways().get(route.gatewayId());
        if (gateway == null) {
            throw new IllegalStateException(
                    "Production AI gateway is not configured: " + route.gatewayId());
        }
        requireConfigured(gateway.baseUrl(), "orgmemory.ai.gateways." + route.gatewayId() + ".base-url");
        requireConfigured(gateway.apiKey(), "orgmemory.ai.gateways." + route.gatewayId() + ".api-key");
        if (!gateway.capabilities().contains(capability)) {
            throw new IllegalStateException(
                    "Production AI gateway " + route.gatewayId() + " must support " + capability);
        }
    }

    private static void requireSecret(String value, String rejectedLocalValue, String property) {
        requireConfigured(value, property);
        if (rejectedLocalValue.equals(value.strip())) {
            throw new IllegalStateException(property + " must not use the local development value in production");
        }
    }

    private static void requireConfigured(String value, String property) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(property + " is required in production");
        }
    }

    private static void requireHttps(URI value, String property) {
        if (value == null || !"https".equalsIgnoreCase(value.getScheme())) {
            throw new IllegalStateException(property + " must use HTTPS in production");
        }
    }
}
