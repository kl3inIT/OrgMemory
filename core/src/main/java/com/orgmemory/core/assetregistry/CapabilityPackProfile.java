package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.profile.AssetPayloadProfile;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

@Component
class CapabilityPackProfile implements AssetPayloadProfile {

    private final ObjectMapper json = JsonMapper.builder().build();

    @Override
    public AssetType type() {
        return AssetType.CAPABILITY_PACK;
    }

    @Override
    public Set<String> schemaVersions() {
        return Set.of("1");
    }

    @Override
    public void validate(String payload) {
        parse(payload);
    }

    CapabilityPackSpec parse(String payload) {
        try {
            return json.readValue(payload, CapabilityPackSpec.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Capability Pack payload does not match schema version 1", exception);
        }
    }
}
