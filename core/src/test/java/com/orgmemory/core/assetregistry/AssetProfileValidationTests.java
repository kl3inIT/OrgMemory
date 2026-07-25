package com.orgmemory.core.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class AssetProfileValidationTests {

    private final PromptTemplateProfile prompts = new PromptTemplateProfile();
    private final WorkInstructionProfile instructions = new WorkInstructionProfile();
    private final CapabilityPackProfile packs = new CapabilityPackProfile();
    private final AssetTypeProfileRegistry registry =
            new AssetTypeProfileRegistry(List.of(prompts, instructions, packs));

    @Test
    void everyEnabledTypeHasAnExplicitSchemaValidator() {
        assertEquals(3, registry.enabledTypes().size());
        registry.require(AssetType.PROMPT_TEMPLATE)
                .validate("1", promptPayload("{{ticket_text}}"));
        registry.require(AssetType.WORK_INSTRUCTION)
                .validate("1", instructionPayload());
        registry.require(AssetType.CAPABILITY_PACK)
                .validate("1", packPayload());
    }

    @Test
    void promptSchemaRejectsMissingTemplateAndDuplicateVariables() {
        String noTemplate = promptPayload("{{ticket_text}}")
                .replace("\"textTemplate\": \"{{ticket_text}}\",", "\"textTemplate\": \"\",");
        assertThrows(
                IllegalArgumentException.class,
                () -> prompts.validate(noTemplate));

        String duplicate = promptPayload("{{ticket_text}}")
                .replace(
                        "\"evaluationCases\": []",
                        """
                        "evaluationCases": [],
                        "variables": [
                          {"name":"ticket_text","type":"STRING","required":true,"sensitive":true},
                          {"name":"ticket_text","type":"STRING","required":true,"sensitive":false}
                        ]
                        """);
        assertThrows(
                IllegalArgumentException.class,
                () -> prompts.validate(duplicate));
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

    static String promptPayload(String template) {
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
}
