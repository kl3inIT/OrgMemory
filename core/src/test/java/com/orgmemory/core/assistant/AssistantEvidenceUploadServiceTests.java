package com.orgmemory.core.assistant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.evidence.GovernedFileUpload;
import com.orgmemory.core.knowledge.evidence.GovernedFileUploadResult;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import java.io.ByteArrayInputStream;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AssistantEvidenceUploadServiceTests {

    @Test
    void delegatesBytesToTheGovernedFilePortBeforeCreatingTheBinding() {
        GovernedFileUpload uploads = mock(GovernedFileUpload.class);
        AssistantEvidenceService evidence = mock(AssistantEvidenceService.class);
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Laura",
                "laura@example.test");
        UUID conversationId = UUID.randomUUID();
        UUID spaceId = UUID.randomUUID();
        GovernedFileUploadResult uploaded = new GovernedFileUploadResult(
                UUID.randomUUID(),
                UUID.randomUUID(),
                spaceId,
                "policy.pdf");
        AssistantEvidenceBindingView expected = mock(AssistantEvidenceBindingView.class);
        when(uploads.upload(any(), any())).thenReturn(uploaded);
        when(evidence.bindUploaded(actor, conversationId, uploaded)).thenReturn(expected);

        AssistantEvidenceBindingView actual = new AssistantEvidenceUploadService(
                        uploads,
                        evidence)
                .upload(
                        actor,
                        conversationId,
                        spaceId,
                        KnowledgeClassification.CONFIDENTIAL,
                        "policy.pdf",
                        4,
                        new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));

        assertSame(expected, actual);
        ArgumentCaptor<com.orgmemory.core.knowledge.evidence.GovernedFileUploadCommand> command =
                ArgumentCaptor.forClass(
                        com.orgmemory.core.knowledge.evidence.GovernedFileUploadCommand.class);
        verify(uploads).upload(command.capture(), any());
        assertSame(actor, command.getValue().actor());
        verify(evidence).bindUploaded(actor, conversationId, uploaded);
        var order = inOrder(evidence, uploads);
        order.verify(evidence).requireUploadConversation(actor, conversationId);
        order.verify(uploads).upload(any(), any());
        order.verify(evidence).bindUploaded(actor, conversationId, uploaded);
    }

    @Test
    void rejectsAnOpaqueConversationBeforeCreatingAGovernedSource() {
        GovernedFileUpload uploads = mock(GovernedFileUpload.class);
        AssistantEvidenceService evidence = mock(AssistantEvidenceService.class);
        CurrentActor actor = new CurrentActor(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Laura",
                "laura@example.test");
        UUID conversationId = UUID.randomUUID();
        doThrow(new BusinessNotFoundException(
                        "assistant.evidence-not-found",
                        "Assistant evidence not found"))
                .when(evidence)
                .requireUploadConversation(actor, conversationId);

        org.junit.jupiter.api.Assertions.assertThrows(
                BusinessNotFoundException.class,
                () -> new AssistantEvidenceUploadService(uploads, evidence).upload(
                        actor,
                        conversationId,
                        UUID.randomUUID(),
                        KnowledgeClassification.CONFIDENTIAL,
                        "policy.pdf",
                        4,
                        new ByteArrayInputStream(new byte[] {1, 2, 3, 4})));

        verifyNoInteractions(uploads);
    }
}
