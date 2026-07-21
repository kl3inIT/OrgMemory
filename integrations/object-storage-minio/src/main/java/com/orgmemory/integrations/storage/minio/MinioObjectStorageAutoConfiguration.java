package com.orgmemory.integrations.storage.minio;

import com.orgmemory.core.knowledge.storage.ObjectStoragePort;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(MinioClient.class)
@ConditionalOnProperty(
        prefix = "orgmemory.storage.object",
        name = {"endpoint", "access-key", "secret-key", "bucket"})
@EnableConfigurationProperties(MinioObjectStorageProperties.class)
public class MinioObjectStorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MinioClient minioClient(MinioObjectStorageProperties properties) {
        return MinioClient.builder()
                .endpoint(properties.endpoint().toString())
                .credentials(properties.accessKey(), properties.secretKey())
                .build();
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    ObjectStoragePort objectStoragePort(MinioClient client, MinioObjectStorageProperties properties) {
        return new MinioObjectStorageAdapter(client, properties);
    }
}
