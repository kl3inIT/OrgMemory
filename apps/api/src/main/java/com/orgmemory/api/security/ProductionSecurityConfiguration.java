package com.orgmemory.api.security;

import com.orgmemory.core.shared.secret.SecretCipherProperties;
import com.orgmemory.integrations.ai.openai.AiGatewayProperties;
import com.orgmemory.integrations.authorization.openfga.OpenFgaAuthorizationProperties;
import com.orgmemory.integrations.storage.minio.MinioObjectStorageProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
class ProductionSecurityConfiguration {

    @Bean
    ProductionConfigurationGuard productionConfigurationGuard(
            DataSourceProperties dataSource,
            OrgMemoryOidcProperties oidc,
            ObjectProvider<OpenFgaAuthorizationProperties> openFga,
            ObjectProvider<MinioObjectStorageProperties> objectStorage,
            AiGatewayProperties ai,
            SecretCipherProperties secrets) {
        OpenFgaAuthorizationProperties requiredOpenFga = openFga.getIfAvailable();
        if (requiredOpenFga == null) {
            throw new IllegalStateException("OpenFGA configuration is required in production");
        }
        MinioObjectStorageProperties requiredObjectStorage = objectStorage.getIfAvailable();
        if (requiredObjectStorage == null) {
            throw new IllegalStateException("Object storage configuration is required in production");
        }
        return new ProductionConfigurationGuard(
                dataSource,
                oidc,
                requiredOpenFga,
                requiredObjectStorage,
                ai,
                secrets);
    }
}
