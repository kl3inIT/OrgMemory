package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.ai.AiGatewayCapability;
import com.orgmemory.core.shared.secret.SecretCipherProperties;
import com.orgmemory.integrations.ai.openai.AiGatewayProperties;
import com.orgmemory.integrations.authorization.openfga.OpenFgaAuthorizationProperties;
import com.orgmemory.integrations.storage.minio.MinioObjectStorageProperties;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.util.unit.DataSize;

class ProductionConfigurationGuardTests {

    @Test
    void acceptsExplicitNonLocalProductionConfiguration() {
        assertDoesNotThrow(() -> guard("database-secret", "oidc-secret", "object-secret")
                .afterPropertiesSet());
    }

    @Test
    void rejectsKnownLocalSecretsAndInsecurePublicOrigins() {
        assertThrows(IllegalStateException.class, () -> guard(
                        "orgmemory", "oidc-secret", "object-secret")
                .afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> guard(
                        "database-secret", "orgmemory-local-dev-only", "object-secret")
                .afterPropertiesSet());
        assertThrows(IllegalStateException.class, () -> guard(
                        "database-secret", "oidc-secret", "orgmemory-local-secret")
                .afterPropertiesSet());

        ProductionConfigurationGuard insecureOrigin = guard(
                "database-secret", "oidc-secret", "object-secret",
                URI.create("http://id.example.test/realms/orgmemory"));
        assertThrows(IllegalStateException.class, insecureOrigin::afterPropertiesSet);
    }

    @Test
    void rejectsTheDevelopmentEncryptionKey() {
        // Every stored source credential is only as private as this key, and the development
        // one is published in this repository.
        assertThrows(
                IllegalStateException.class,
                () -> guardWithSecretsKey("orgmemory-local-dev-only-secret-key").afterPropertiesSet());
        assertDoesNotThrow(() -> guardWithSecretsKey("a-production-encryption-key").afterPropertiesSet());
    }

    @Test
    void rejectsAnEmbeddingRouteWithoutAnEmbeddingCapableGateway() {
        var dataSource = new DataSourceProperties();
        dataSource.setUrl("jdbc:postgresql://db.example.test/orgmemory");
        dataSource.setUsername("orgmemory_app");
        dataSource.setPassword("database-secret");
        var ai = new AiGatewayProperties(
                Map.of("openai", new AiGatewayProperties.Gateway(
                        "OpenAI",
                        "https://api.openai.com/v1",
                        "provider-secret",
                        Set.of(AiGatewayCapability.CHAT),
                        Duration.ofSeconds(60))),
                new AiGatewayProperties.Routes(
                        new AiGatewayProperties.Route("openai", "gpt-5.6-sol"),
                        new AiGatewayProperties.Route("openai", "gpt-5.6-sol"),
                        new AiGatewayProperties.Route("openai", "text-embedding-3-large")));
        ProductionConfigurationGuard guard = new ProductionConfigurationGuard(
                dataSource,
                new OrgMemoryOidcProperties(
                        URI.create("https://id.example.test/realms/orgmemory"),
                        "orgmemory-web",
                        "oidc-secret",
                        URI.create("https://memory.example.test")),
                openFga(),
                objectStorage("object-secret"),
                ai,
                secrets("a-production-encryption-key"));

        assertThrows(IllegalStateException.class, guard::afterPropertiesSet);
    }

    private static ProductionConfigurationGuard guard(
            String databasePassword,
            String oidcSecret,
            String objectStorageSecret) {
        return guard(
                databasePassword,
                oidcSecret,
                objectStorageSecret,
                URI.create("https://id.example.test/realms/orgmemory"));
    }

    private static ProductionConfigurationGuard guard(
            String databasePassword,
            String oidcSecret,
            String objectStorageSecret,
            URI issuer) {
        var dataSource = new DataSourceProperties();
        dataSource.setUrl("jdbc:postgresql://db.example.test/orgmemory");
        dataSource.setUsername("orgmemory_app");
        dataSource.setPassword(databasePassword);
        var oidc = new OrgMemoryOidcProperties(
                issuer,
                "orgmemory-web",
                oidcSecret,
                URI.create("https://memory.example.test"));
        var openFga = openFga();
        var objectStorage = objectStorage(objectStorageSecret);
        var ai = new AiGatewayProperties(
                Map.of("openai", new AiGatewayProperties.Gateway(
                        "OpenAI",
                        "https://api.openai.com/v1",
                        "provider-secret",
                        Set.of(AiGatewayCapability.CHAT, AiGatewayCapability.EMBEDDING),
                        Duration.ofSeconds(60))),
                new AiGatewayProperties.Routes(
                        new AiGatewayProperties.Route("openai", "gpt-5.6-sol"),
                        new AiGatewayProperties.Route("openai", "gpt-5.6-sol"),
                        new AiGatewayProperties.Route("openai", "text-embedding-3-large")));
        return new ProductionConfigurationGuard(
                dataSource, oidc, openFga, objectStorage, ai, secrets("a-production-encryption-key"));
    }

    private static ProductionConfigurationGuard guardWithSecretsKey(String key) {
        var dataSource = new DataSourceProperties();
        dataSource.setUrl("jdbc:postgresql://db.example.test/orgmemory");
        dataSource.setUsername("orgmemory_app");
        dataSource.setPassword("database-secret");
        return new ProductionConfigurationGuard(
                dataSource,
                new OrgMemoryOidcProperties(
                        URI.create("https://id.example.test/realms/orgmemory"),
                        "orgmemory-web",
                        "oidc-secret",
                        URI.create("https://memory.example.test")),
                openFga(),
                objectStorage("object-secret"),
                productionAi(),
                secrets(key));
    }

    private static AiGatewayProperties productionAi() {
        return new AiGatewayProperties(
                Map.of("openai", new AiGatewayProperties.Gateway(
                        "OpenAI",
                        "https://api.openai.com/v1",
                        "provider-secret",
                        Set.of(AiGatewayCapability.CHAT, AiGatewayCapability.EMBEDDING),
                        Duration.ofSeconds(60))),
                new AiGatewayProperties.Routes(
                        new AiGatewayProperties.Route("openai", "gpt-5.6-sol"),
                        new AiGatewayProperties.Route("openai", "gpt-5.6-sol"),
                        new AiGatewayProperties.Route("openai", "text-embedding-3-large")));
    }

    private static SecretCipherProperties secrets(String key) {
        return new SecretCipherProperties(key, null, 1);
    }

    private static OpenFgaAuthorizationProperties openFga() {
        return new OpenFgaAuthorizationProperties(
                URI.create("https://fga.example.test"),
                "store-1",
                "model-1",
                3,
                Duration.ofMillis(100),
                Duration.ofSeconds(5));
    }

    private static MinioObjectStorageProperties objectStorage(String objectStorageSecret) {
        return new MinioObjectStorageProperties(
                URI.create("https://objects.example.test"),
                "orgmemory-production",
                objectStorageSecret,
                "orgmemory-evidence",
                DataSize.ofMegabytes(25));
    }
}
