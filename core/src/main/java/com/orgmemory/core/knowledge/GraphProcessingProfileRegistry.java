package com.orgmemory.core.knowledge;

import com.orgmemory.graphrag.processing.GraphProcessingProfile;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Insert-only registry for reproducible graph-processing profiles. */
@Service
public class GraphProcessingProfileRegistry {

    private final GraphProcessingProfileRepository profiles;
    private final JdbcClient jdbc;

    GraphProcessingProfileRegistry(
            GraphProcessingProfileRepository profiles,
            JdbcClient jdbc) {
        this.profiles = profiles;
        this.jdbc = jdbc;
    }

    @Transactional
    public GraphProcessingProfileRef resolve(GraphProcessingProfile profile) {
        Objects.requireNonNull(profile, "profile");
        UUID id = UUID.nameUUIDFromBytes(
                ("graph-processing:" + profile.canonicalSha256())
                        .getBytes(StandardCharsets.UTF_8));
        jdbc.sql("""
                        INSERT INTO graph_processing_profiles (
                            id, canonical_sha256, canonical_form, created_at
                        ) VALUES (
                            :id, :canonicalSha256, :canonicalForm, :createdAt
                        )
                        ON CONFLICT (canonical_sha256) DO NOTHING
                        """)
                .param("id", id)
                .param("canonicalSha256", profile.canonicalSha256())
                .param("canonicalForm", profile.canonicalForm())
                .param("createdAt", OffsetDateTime.now(ZoneOffset.UTC))
                .update();
        GraphProcessingProfileRef resolved = profiles
                .findByCanonicalSha256(profile.canonicalSha256())
                .orElseThrow(() -> new IllegalStateException(
                        "graph processing profile registration failed"))
                .toRef();
        if (!resolved.profile().equals(profile)) {
            throw new IllegalStateException(
                    "graph processing profile hash is bound to different settings");
        }
        return resolved;
    }

    @Transactional(readOnly = true)
    public GraphProcessingProfileRef get(UUID profileId) {
        return profiles.findById(Objects.requireNonNull(profileId, "profileId"))
                .orElseThrow(() -> new IllegalStateException(
                        "graph processing profile was not found"))
                .toRef();
    }
}
