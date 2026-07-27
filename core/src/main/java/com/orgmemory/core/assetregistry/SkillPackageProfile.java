package com.orgmemory.core.assetregistry;

import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
class SkillPackageProfile implements AssetPayloadProfile {

    static final String SCHEMA_VERSION = "1";

    private final ObjectMapper json = JsonMapper.builder().build();

    @Override
    public AssetType type() {
        return AssetType.SKILL;
    }

    @Override
    public Set<String> schemaVersions() {
        return Set.of(SCHEMA_VERSION);
    }

    @Override
    public void validate(String payload) {
        parse(payload);
    }

    SkillPackageSpec parse(String payload) {
        try {
            return json.readValue(payload, SkillPackageSpec.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Skill package payload does not match schema version 1", exception);
        }
    }
}
