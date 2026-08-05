package com.orgmemory.integrations.ai.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.orgmemory.core.assetregistry.skill.SkillRuntimeOperations;
import com.orgmemory.core.assistant.AssistantAgentActivity;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;

class AssistantSkillToolCallbacksTests {

    private static final UUID ASSET_ID = UUID.fromString(
            "92000000-0000-0000-0000-000000000001");
    private static final UUID RELEASE_ID = UUID.fromString(
            "92000000-0000-0000-0000-000000000002");
    private static final CurrentActor ACTOR = new CurrentActor(
            UUID.fromString("92000000-0000-0000-0000-000000000003"),
            UUID.fromString("92000000-0000-0000-0000-000000000004"),
            null,
            "Skill user",
            "skill.user@example.test",
            UserRole.EMPLOYEE);

    @Test
    void exposesOnlyTheFixedReadOnlyProgressiveDisclosureTools() {
        SkillRuntimeOperations skills = mock(SkillRuntimeOperations.class);
        List<ToolCallback> callbacks =
                new AssistantSkillToolCallbacks(skills).create(ACTOR, ignored -> { });

        assertEquals(
                List.of("search_skills", "activate_skill", "read_skill_resource"),
                callbacks.stream()
                        .map(tool -> tool.getToolDefinition().name())
                        .toList());
    }

    @Test
    void searchUsesTheCurrentActorAndEmitsBoundedProgress() {
        SkillRuntimeOperations skills = mock(SkillRuntimeOperations.class);
        SkillRuntimeOperations.SkillSummary summary = summary();
        when(skills.search(ACTOR, "incident", 3)).thenReturn(List.of(summary));
        List<AssistantAgentActivity> activities = new ArrayList<>();
        ToolCallback search = new AssistantSkillToolCallbacks(skills)
                .create(ACTOR, activities::add)
                .getFirst();

        String result = search.call("{\"query\":\"incident\",\"limit\":3}");

        assertTrue(result.contains("support/incident-response"));
        assertEquals(List.of(
                        new AssistantAgentActivity(
                                AssistantAgentActivity.Phase.SKILL_DISCOVERY,
                                AssistantAgentActivity.State.ACTIVE,
                                null),
                        new AssistantAgentActivity(
                                AssistantAgentActivity.Phase.SKILL_DISCOVERY,
                                AssistantAgentActivity.State.COMPLETE,
                                1)),
                activities);
        verify(skills).search(ACTOR, "incident", 3);
    }

    @Test
    void activationReturnsInstructionsWithoutTurningAllowedToolsIntoAuthority() {
        SkillRuntimeOperations skills = mock(SkillRuntimeOperations.class);
        when(skills.activate(ACTOR, ASSET_ID, RELEASE_ID)).thenReturn(
                new SkillRuntimeOperations.ActivatedSkill(
                        summary(),
                        "Follow the approved incident workflow.",
                        List.of("references/runbook.md")));
        ToolCallback activate = new AssistantSkillToolCallbacks(skills)
                .create(ACTOR, ignored -> { })
                .get(1);

        String result = activate.call("{\"assetId\":\"" + ASSET_ID
                + "\",\"releaseId\":\"" + RELEASE_ID + "\"}");

        assertTrue(result.contains("Follow the approved incident workflow."));
        assertTrue(result.contains("references/runbook.md"));
        assertFalse(result.contains("allowed-tools"));
        verify(skills).activate(ACTOR, ASSET_ID, RELEASE_ID);
    }

    @Test
    void failuresStayOpaqueToTheModel() {
        SkillRuntimeOperations skills = mock(SkillRuntimeOperations.class);
        when(skills.activate(ACTOR, ASSET_ID, RELEASE_ID))
                .thenThrow(new IllegalStateException("private object key and tenant details"));
        ToolCallback activate = new AssistantSkillToolCallbacks(skills)
                .create(ACTOR, ignored -> { })
                .get(1);

        String result = activate.call("{\"assetId\":\"" + ASSET_ID
                + "\",\"releaseId\":\"" + RELEASE_ID + "\"}");

        assertTrue(result.contains("The requested Skill is unavailable."));
        assertFalse(result.contains("private object key"));
        assertFalse(result.contains("tenant details"));
    }

    @Test
    void boundsTheTotalCallsAcrossAllToolsInOneAssistantTurn() {
        SkillRuntimeOperations skills = mock(SkillRuntimeOperations.class);
        when(skills.search(ACTOR, "loop", 1)).thenReturn(List.of());
        ToolCallback search = new AssistantSkillToolCallbacks(skills)
                .create(ACTOR, ignored -> { })
                .getFirst();

        String thirteenth = "";
        for (int index = 0; index < 13; index++) {
            thirteenth = search.call("{\"query\":\"loop\",\"limit\":1}");
        }

        assertTrue(thirteenth.contains("The requested Skill is unavailable."));
        verify(skills, times(12)).search(ACTOR, "loop", 1);
    }

    private static SkillRuntimeOperations.SkillSummary summary() {
        return new SkillRuntimeOperations.SkillSummary(
                ASSET_ID,
                RELEASE_ID,
                "support/incident-response",
                "1.0.0",
                "Incident response",
                "Coordinate incidents safely",
                "a".repeat(64));
    }
}
