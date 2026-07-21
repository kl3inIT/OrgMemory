package com.orgmemory.core.knowledge;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmbeddingProfileRegistry {

    private final EmbeddingProfileRepository profiles;
    private final JdbcClient jdbc;

    EmbeddingProfileRegistry(EmbeddingProfileRepository profiles, JdbcClient jdbc) {
        this.profiles = profiles;
        this.jdbc = jdbc;
    }

    @Transactional
    public EmbeddingProfileRef resolve(UUID organizationId, EmbeddingProfileSpec spec) {
        if (organizationId == null || spec == null) {
            throw new IllegalArgumentException("organization and embedding profile are required");
        }
        String profileKey = spec.profileKey();
        if (profileKey.length() > 255) {
            throw new IllegalArgumentException("embedding profile key is too long");
        }
        UUID id = UUID.nameUUIDFromBytes(
                (organizationId + ":" + profileKey).getBytes(StandardCharsets.UTF_8));
        jdbc.sql("""
                        INSERT INTO embedding_profiles (
                            id, organization_id, profile_key, provider, model,
                            dimensions, distance_metric, created_at
                        ) VALUES (
                            :id, :organizationId, :profileKey, :provider, :model,
                            :dimensions, :distanceMetric, :createdAt
                        )
                        ON CONFLICT (organization_id, profile_key) DO NOTHING
                        """)
                .param("id", id)
                .param("organizationId", organizationId)
                .param("profileKey", profileKey)
                .param("provider", spec.provider())
                .param("model", spec.model())
                .param("dimensions", spec.dimensions())
                .param("distanceMetric", spec.distanceMetric().name())
                .param("createdAt", OffsetDateTime.now(ZoneOffset.UTC))
                .update();
        return profiles.findByOrganizationIdAndProfileKey(organizationId, profileKey)
                .orElseThrow(() -> new IllegalStateException("embedding profile registration failed"))
                .toRef();
    }

    @Transactional(readOnly = true)
    public EmbeddingProfileRef get(UUID organizationId, UUID profileId) {
        return profiles.findByIdAndOrganizationId(profileId, organizationId)
                .orElseThrow(() -> new IllegalStateException("embedding profile was not found"))
                .toRef();
    }
}
