package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.TEXT_EVENT_STREAM;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.ai.AssistantModelAuthorityService;
import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.assistant.AssistantEvidenceService;
import com.orgmemory.core.assistant.AssistantEvidenceUploadService;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.knowledge.retrieval.CitationEvidenceService;
import jakarta.validation.Validation;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AssistantChatRequestValidationTests {

    @Test
    void enforcesTheMessageLimitBoundary() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var accepted = validator.validate(
                    new AssistantChatRequest("a".repeat(1_000), null, null, null));
            var rejected = validator.validate(
                    new AssistantChatRequest("a".repeat(1_001), null, null, null));

            assertEquals(0, accepted.size());
            assertEquals(1, rejected.size());
            assertEquals(
                    "message",
                    rejected.iterator().next().getPropertyPath().toString());
        }
    }

    @Test
    void limitsOneTurnToThreeEvidenceBindings() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var violations = factory.getValidator().validate(new AssistantChatRequest(
                    "Compare these files",
                    null,
                    null,
                    null,
                    List.of(
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID(),
                            UUID.randomUUID())));

            assertEquals(1, violations.size());
            assertEquals(
                    "evidenceBindingIds",
                    violations.iterator().next().getPropertyPath().toString());
        }
    }

    @Test
    void rejectsAnOversizedMessageBeforeOpeningTheStreamOrCreatingATurn() throws Exception {
        var assistant = mock(AssistantService.class);
        var conversations = mock(AssistantConversationService.class);
        var actors = mock(CurrentActorProvider.class);
        var properties = mock(AssistantProperties.class);
        var modelAuthority = mock(AssistantModelAuthorityService.class);
        var citationEvidence = mock(CitationEvidenceService.class);
        var retrievalScheduler = mock(AssistantRetrievalScheduler.class);
        var json = mock(ObjectMapper.class);
        var mvc = standaloneSetup(new AssistantController(
                        assistant,
                        conversations,
                        actors,
                        properties,
                        modelAuthority,
                        citationEvidence,
                        retrievalScheduler,
                        mock(AssistantEvidenceUploadService.class),
                        mock(AssistantEvidenceService.class),
                        json))
                .build();

        mvc.perform(post("/api/assistant/chat")
                        .contentType(APPLICATION_JSON)
                        .accept(TEXT_EVENT_STREAM)
                        .content("{\"message\":\"" + "a".repeat(1_001) + "\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(
                assistant,
                conversations,
                actors,
                properties,
                modelAuthority,
                citationEvidence,
                retrievalScheduler,
                json);
    }
}
