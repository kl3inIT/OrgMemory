package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AssistantAgentActivityTests {

    @Test
    void sanitizesAndBoundsSuccessfulActivationTitles() {
        AssistantAgentActivity activity = new AssistantAgentActivity(
                AssistantAgentActivity.Phase.SKILL_ACTIVATION,
                AssistantAgentActivity.State.COMPLETE,
                null,
                1,
                "  Incident\n\tresponse\u202e " + "workflow ".repeat(20));

        assertEquals(80, activity.skillTitle().codePointCount(0, activity.skillTitle().length()));
        assertTrue(activity.skillTitle().startsWith("Incident response workflow"));
        assertTrue(activity.skillTitle().endsWith("…"));
        assertFalse(activity.skillTitle().contains("\u202e"));
    }

    @Test
    void rejectsIdentityOutsideSuccessfulActivation() {
        assertThrows(IllegalArgumentException.class, () -> new AssistantAgentActivity(
                AssistantAgentActivity.Phase.SKILL_ACTIVATION,
                AssistantAgentActivity.State.FAILED,
                null,
                1,
                "Private skill"));
        assertThrows(IllegalArgumentException.class, () -> new AssistantAgentActivity(
                AssistantAgentActivity.Phase.SKILL_DISCOVERY,
                AssistantAgentActivity.State.COMPLETE,
                1,
                1,
                null));
        assertThrows(IllegalArgumentException.class, () -> new AssistantAgentActivity(
                AssistantAgentActivity.Phase.SKILL_ACTIVATION,
                AssistantAgentActivity.State.COMPLETE,
                null,
                1,
                "\u00a0\u00a0"));
    }
}
