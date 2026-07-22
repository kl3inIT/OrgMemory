package com.orgmemory.api.assistant;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assistant.AssistantTurn;
import com.orgmemory.core.knowledge.RetrievedKnowledgeEvidence;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/assistant")
class AssistantController {

    private static final String UI_MESSAGE_STREAM_HEADER = "x-vercel-ai-ui-message-stream";
    private static final String TEXT_PART_ID = "answer";

    private final AssistantService assistant;
    private final CurrentActorProvider actors;
    private final AssistantProperties properties;
    private final ObjectMapper json;

    AssistantController(
            AssistantService assistant,
            CurrentActorProvider actors,
            AssistantProperties properties,
            ObjectMapper json) {
        this.assistant = assistant;
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
        AssistantTurn turn = assistant.startTurn(
                actors.current(authentication), request.message(), request.limit(), requestId);
        Flux<AssistantStreamPart> parts = parts(turn);
        return ResponseEntity.ok()
                .header("X-Request-ID", turn.requestId())
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

    private static Flux<AssistantStreamPart> parts(AssistantTurn turn) {
        Flux<AssistantStreamPart> text = turn.content()
                .map(token -> (AssistantStreamPart) new AssistantStreamPart.TextDelta(TEXT_PART_ID, token))
                .switchOnFirst((signal, tokens) -> signal.hasValue()
                        ? Flux.concat(
                                Flux.just(new AssistantStreamPart.TextStart(TEXT_PART_ID)),
                                tokens,
                                Flux.just(new AssistantStreamPart.TextEnd(TEXT_PART_ID)))
                        : tokens);
        return Flux.concat(
                Flux.just(new AssistantStreamPart.StartStep()),
                Flux.fromIterable(turn.evidence()).map(AssistantController::sourcePart),
                text,
                Flux.just(new AssistantStreamPart.FinishStep()));
    }

    private static AssistantStreamPart sourcePart(RetrievedKnowledgeEvidence evidence) {
        String sourceId = "urn:orgmemory:citation:" + evidence.chunkId();
        String title = evidence.startPage() == null
                ? evidence.title()
                : evidence.title() + " · page " + evidence.startPage();
        if (evidence.sourceUri() != null && !evidence.sourceUri().isBlank()) {
            return new AssistantStreamPart.SourceUrl(sourceId, evidence.sourceUri(), title);
        }
        return new AssistantStreamPart.SourceDocument(
                sourceId, "application/octet-stream", title, evidence.title());
    }
}
