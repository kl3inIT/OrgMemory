package com.orgmemory.core.assetregistry.workinstruction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.core.assetregistry.api.AssetType;
import org.junit.jupiter.api.Test;

class WorkInstructionProfileTests {

    private final WorkInstructionProfile profile = new WorkInstructionProfile();

    @Test
    void validatesAndParsesTheVersionOneSchema() {
        var parsed = profile.parse(validPayload());

        assertEquals(AssetType.WORK_INSTRUCTION, profile.type());
        assertEquals("Respond safely", parsed.purpose());
        assertEquals("triage", parsed.steps().getFirst().key());
    }

    @Test
    void preservesTheStableValidationFailure() {
        var failure = assertThrows(
                IllegalArgumentException.class,
                () -> profile.validate("{}"));

        assertEquals(
                "Work Instruction payload does not match schema version 1",
                failure.getMessage());
    }

    private static String validPayload() {
        return """
                {
                  "purpose": "Respond safely",
                  "audience": "L1 support",
                  "prerequisites": ["Assigned ticket"],
                  "completionOutcome": "Customer receives a response",
                  "responsibleRole": "Support agent",
                  "steps": [{
                    "key": "triage",
                    "title": "Triage",
                    "instruction": "Read the ticket",
                    "expectedResult": "Category selected",
                    "check": "Category is approved",
                    "escalation": "Escalate legal threats",
                    "prohibitedActions": [],
                    "relatedAssetIds": [],
                    "relatedKnowledgeVersionIds": []
                  }]
                }
                """;
    }
}
