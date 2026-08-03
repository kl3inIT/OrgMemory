package com.orgmemory.core.assetregistry.workinstruction;

import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.profile.AssetPayloadProfile;
import com.orgmemory.core.assetregistry.workinstructioncontract.WorkInstructionSpec;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@Component
class WorkInstructionProfile implements AssetPayloadProfile {

    private final ObjectMapper json = JsonMapper.builder().build();

    @Override
    public AssetType type() {
        return AssetType.WORK_INSTRUCTION;
    }

    @Override
    public Set<String> schemaVersions() {
        return Set.of("1");
    }

    @Override
    public void validate(String payload) {
        parse(payload);
    }

    WorkInstructionSpec parse(String payload) {
        try {
            return json.readValue(payload, WorkInstructionSpec.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Work Instruction payload does not match schema version 1", exception);
        }
    }
}
