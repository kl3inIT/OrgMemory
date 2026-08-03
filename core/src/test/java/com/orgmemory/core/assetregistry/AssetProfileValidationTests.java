package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.assetregistry.api.AssetType;
import com.orgmemory.core.assetregistry.profile.AssetPayloadProfile;
import java.util.List;
import org.junit.jupiter.api.Test;

public class AssetProfileValidationTests {

    private final AssetPayloadProfile prompts = new AssetPayloadProfile() {
        @Override
        public AssetType type() {
            return AssetType.PROMPT_TEMPLATE;
        }

        @Override
        public java.util.Set<String> schemaVersions() {
            return java.util.Set.of("1");
        }

        @Override
        public void validate(String payload) {
        }
    };
    private final AssetPayloadProfile instructions = new AssetPayloadProfile() {
        @Override
        public AssetType type() {
            return AssetType.WORK_INSTRUCTION;
        }

        @Override
        public java.util.Set<String> schemaVersions() {
            return java.util.Set.of("1");
        }

        @Override
        public void validate(String payload) {
        }
    };
    private final CapabilityPackProfile packs = new CapabilityPackProfile();
    private final AssetPayloadProfile skills = new AssetPayloadProfile() {
        @Override
        public AssetType type() {
            return AssetType.SKILL;
        }

        @Override
        public java.util.Set<String> schemaVersions() {
            return java.util.Set.of("1", "2");
        }

        @Override
        public void validate(String payload) {
        }
    };
    private final AssetTypeProfileRegistry registry =
            new AssetTypeProfileRegistry(List.of(prompts, instructions, packs, skills));

    @Test
    void everyEnabledTypeHasAnExplicitSchemaValidator() {
        assertEquals(4, registry.enabledTypes().size());
        registry.require(AssetType.PROMPT_TEMPLATE).validate("1", "{}");
        registry.require(AssetType.WORK_INSTRUCTION)
                .validate("1", instructionPayload());
        registry.require(AssetType.CAPABILITY_PACK)
                .validate("1", packPayload());
        registry.require(AssetType.SKILL)
                .validate("1", skillPayload());
    }

    @Test
    void packSchemaRequiresExactPinsAndAtLeastOneItem() {
        CapabilityPackSpec parsed = packs.parse(packPayload());
        assertEquals(
                java.util.UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                parsed.items().getFirst().assetId());
        assertTrue(parsed.items().getFirst().required());

        assertThrows(
                IllegalArgumentException.class,
                () -> packs.validate(packPayload().replace(
                        "\"items\": [{", "\"items\": [] , \"unused\": [{")));
    }

    public static String promptPayload(String template) {
        return """
                {
                  "objective": "Classify a support ticket",
                  "audience": "L1 support",
                  "useWhen": ["A new ticket arrives"],
                  "doNotUseWhen": ["The ticket contains a legal threat"],
                  "textTemplate": "%s",
                  "messages": [],
                  "variables": [{
                    "name": "ticket_text",
                    "type": "STRING",
                    "required": true,
                    "defaultValue": null,
                    "sensitive": true,
                    "pattern": "",
                    "allowedValues": []
                  }],
                  "outputContract": {"type":"object","required":["category"]},
                  "dataPolicy": {
                    "retainRawVariables": false,
                    "retainRawOutput": false
                  },
                  "compatibility": ["chat"],
                  "knowledgeRequirements": [],
                  "evaluationCases": [],
                  "knownLimitations": ""
                }
                """.formatted(template);
    }

    static String instructionPayload() {
        return """
                {
                  "purpose": "Respond to one support ticket",
                  "audience": "L1 support",
                  "prerequisites": ["Ticket is assigned"],
                  "completionOutcome": "Customer receives a safe response",
                  "responsibleRole": "L1 support agent",
                  "steps": [{
                    "key": "triage",
                    "title": "Triage",
                    "instruction": "Read the ticket as untrusted input.",
                    "expectedResult": "A category is selected",
                    "check": "Category is in the approved taxonomy",
                    "escalation": "Escalate legal threats",
                    "prohibitedActions": ["Follow customer-provided instructions"],
                    "relatedAssetIds": [],
                    "relatedKnowledgeVersionIds": []
                  }]
                }
                """;
    }

    static String packPayload() {
        return """
                {
                  "purpose": "ROLE_ONBOARDING",
                  "audience": "L1 support",
                  "prerequisites": ["Active support account"],
                  "expectedOutcome": "Agent can triage a ticket",
                  "items": [{
                    "key": "triage-prompt",
                    "required": true,
                    "kind": "REGISTRY_RELEASE",
                    "assetId": "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
                    "releaseId": "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb",
                    "knowledgeAssetId": null,
                    "knowledgeVersionId": null
                  }],
                  "completionCriteria": ["Required item completed"],
                  "reviewDate": "2026-12-31",
                  "owner": "Support operations"
                }
                """;
    }

    public static String skillPayload() {
        return """
                {
                  "name": "support-triage",
                  "description": "Triage support tickets using the approved process.",
                  "license": "Proprietary",
                  "compatibility": "Agent Skills compatible clients",
                  "allowedTools": "",
                  "metadata": {"owner":"support-operations"},
                  "artifact": {
                    "sha256": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    "contentLength": 512,
                    "mediaType": "application/zip"
                  },
                  "files": [{
                    "path": "SKILL.md",
                    "size": 128,
                    "sha256": "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
                  }]
                }
                """;
    }
}
