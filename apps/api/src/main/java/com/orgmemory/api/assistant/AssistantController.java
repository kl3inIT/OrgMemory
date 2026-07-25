package com.orgmemory.api.assistant;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assistant.AssistantCitation;
import com.orgmemory.core.assistant.AssistantConversationMessageView;
import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.assistant.AssistantConversationSummary;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assistant.AssistantTurn;
import com.orgmemory.core.organization.CurrentActor;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/assistant")
class AssistantController {

    private static final String UI_MESSAGE_STREAM_HEADER = "x-vercel-ai-ui-message-stream";
    private static final String TEXT_PART_ID = "answer";

    private final AssistantService assistant;
    private final AssistantConversationService conversations;
    private final ChatMemory memory;
    private final CurrentActorProvider actors;
    private final AssistantProperties properties;
    private final ObjectMapper json;

    AssistantController(
            AssistantService assistant,
            AssistantConversationService conversations,
            ChatMemory memory,
            CurrentActorProvider actors,
            AssistantProperties properties,
            ObjectMapper json) {
        this.assistant = assistant;
        this.conversations = conversations;
        this.memory = memory;
        this.actors = actors;
        this.properties = properties;
        this.json = json;
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "streamAssistantChat", summary = "Stream an answer from permission-verified knowledge")
    ResponseEntity<Flux<ServerSentEvent<String>>> chat(
            @Valid @RequestBody AssistantChatRequest request,
            Authentication authentication) {
        String requestId = UUID.randomUUID().toString();
        CurrentActor actor = actors.current(authentication);
        UUID conversationId = conversations.beginTurn(
                actor, request.conversationId(), request.message());
        AssistantTurn turn = assistant.startTurn(
                actor,
                request.message(),
                request.limit(),
                requestId,
                conversationId.toString());
        StringBuilder completedAnswer = new StringBuilder();
        Flux<AssistantStreamPart> parts = parts(turn)
                .doOnNext(part -> {
                    if (part instanceof AssistantStreamPart.TextDelta delta) {
                        completedAnswer.append(delta.delta());
                    }
                })
                .doOnComplete(() -> conversations.completeTurn(
                        actor, conversationId, completedAnswer.toString()));
        return ResponseEntity.ok()
                .header("X-Request-ID", turn.requestId())
                .header("X-Conversation-ID", conversationId.toString())
                .header(UI_MESSAGE_STREAM_HEADER, "v1")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(UiMessageStream.encode(
                        parts,
                        json,
                        properties.heartbeatInterval(),
                        properties.turnTimeout()));
    }

    record RenameConversationRequest(
            @NotBlank @Size(max = 120) String title) {
    }

    @GetMapping("/conversations")
    @Operation(
            operationId = "listAssistantConversations",
            summary = "List the current actor's conversations by recent activity")
    List<AssistantConversationSummary> conversations(Authentication authentication) {
        return conversations.list(actors.current(authentication));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    @Operation(
            operationId = "getAssistantConversationHistory",
            summary = "Replay a tenant- and actor-scoped full conversation transcript")
    List<AssistantConversationMessageView> history(
            @PathVariable UUID conversationId,
            Authentication authentication) {
        return conversations.history(
                actors.current(authentication), conversationId);
    }

    @PatchMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "renameAssistantConversation",
            summary = "Rename the current actor's conversation")
    void rename(
            @PathVariable UUID conversationId,
            @Valid @RequestBody RenameConversationRequest request,
            Authentication authentication) {
        conversations.rename(
                actors.current(authentication), conversationId, request.title());
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "deleteAssistantConversation",
            summary = "Delete the current actor's transcript and bounded model memory")
    void delete(
            @PathVariable UUID conversationId,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        conversations.delete(actor, conversationId);
        memory.clear(conversationId.toString());
    }

    Flux<AssistantStreamPart> parts(AssistantTurn turn) {
        Flux<AssistantStreamPart> text = turn.content()
                .map(token -> (AssistantStreamPart)
                        new AssistantStreamPart.TextDelta(
                                TEXT_PART_ID,
                                token))
                .switchOnFirst((signal, streamedTokens) ->
                        signal.hasValue()
                                ? Flux.concat(
                                        Flux.just(new AssistantStreamPart
                                                .TextStart(TEXT_PART_ID)),
                                        streamedTokens,
                                        Flux.just(new AssistantStreamPart
                                                .TextEnd(TEXT_PART_ID)))
                                : streamedTokens);
        return Flux.concat(
                Flux.just(new AssistantStreamPart.StartStep()),
                Flux.fromIterable(turn.citations())
                        .map(AssistantController::sourcePart),
                text,
                Flux.just(new AssistantStreamPart.FinishStep()));
    }

    private static AssistantStreamPart sourcePart(AssistantCitation citation) {
        var evidence = citation.evidence();
        String sourceId = "urn:orgmemory:citation:"
                + citation.number()
                + ":"
                + evidence.chunkId();
        String title = evidence.startPage() == null
                ? evidence.title()
                : evidence.title() + " · page " + evidence.startPage();
        return new AssistantStreamPart.SourceUrl(
                sourceId,
                "/api/citations/" + evidence.chunkId() + "/content",
                title,
                citation.number());
    }
}
