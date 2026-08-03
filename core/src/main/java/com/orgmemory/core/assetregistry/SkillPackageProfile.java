package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.profile.AssetPayloadProfile;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackageArtifact;
import com.orgmemory.core.assetregistry.skillpackage.SkillPackagePayloadPolicy;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

@Component
class SkillPackageProfile implements
        AssetPayloadProfile,
        SkillPackageSpecReader,
        SkillPackagePayloadPolicy {

    static final String SCHEMA_VERSION = "2";

    private final ObjectMapper json = JsonMapper.builder().build();

    @Override
    public AssetType type() {
        return AssetType.SKILL;
    }

    @Override
    public Set<String> schemaVersions() {
        return Set.of("1", SCHEMA_VERSION);
    }

    @Override
    public void validate(String payload) {
        read(payload);
    }

    @Override
    public void validate(
            String canonicalPayload, SkillPackageArtifact artifact) {
        SkillPackageSpec spec = read(canonicalPayload);
        if (!spec.artifact().sha256().equals(artifact.sha256())
                || spec.artifact().contentLength() != artifact.contentLength()
                || !spec.artifact().mediaType().equals(artifact.mediaType())) {
            throw new SkillPackageValidationException(
                    "The Skill package artifact does not match its canonical payload");
        }
    }

    @Override
    public SkillPackageSpec read(String payload) {
        try {
            return json.readValue(payload, SkillPackageSpec.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Skill package payload does not match a supported schema version", exception);
        }
    }
}

interface SkillPackageSpecReader {

    SkillPackageSpec read(String payload);
}
