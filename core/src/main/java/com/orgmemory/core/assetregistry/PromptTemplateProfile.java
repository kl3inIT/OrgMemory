package com.orgmemory.core.assetregistry;

import com.orgmemory.core.assetregistry.api.AssetType;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ObjectMapper;

@Component
class PromptTemplateProfile implements AssetPayloadProfile {

    private final ObjectMapper json = JsonMapper.builder().build();

    @Override
    public AssetType type() {
        return AssetType.PROMPT_TEMPLATE;
    }

    @Override
    public Set<String> schemaVersions() {
        return Set.of("1");
    }

    @Override
    public void validate(String payload) {
        parse(payload);
    }

    PromptTemplateSpec parse(String payload) {
        try {
            return json.readValue(payload, PromptTemplateSpec.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Prompt Template payload does not match schema version 1", exception);
        }
    }
}
