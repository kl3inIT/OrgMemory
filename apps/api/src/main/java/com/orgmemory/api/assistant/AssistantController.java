package com.orgmemory.api.assistant;

import com.orgmemory.api.ApiRequestException;
import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assistant.AssistantAnswerFeedbackView;
import com.orgmemory.core.assistant.AssistantAnswerSentiment;
import com.orgmemory.core.assistant.AssistantAgentActivity;
import com.orgmemory.core.assistant.AssistantCitation;
import com.orgmemory.core.assistant.AssistantCitationEvidence;
import com.orgmemory.core.assistant.AssistantConversationMessageView;
import com.orgmemory.core.assistant.AssistantConversationService;
import com.orgmemory.core.assistant.AssistantConversationSummary;
import com.orgmemory.core.assistant.AssistantEvidenceTurnClaim;
import com.orgmemory.core.assistant.AssistantEvidenceBindingView;
import com.orgmemory.core.assistant.AssistantEvidenceService;
import com.orgmemory.core.assistant.AssistantEvidenceUploadService;
import com.orgmemory.core.assistant.AssistantFileContent;
import com.orgmemory.core.assistant.AssistantFilePage;
import com.orgmemory.core.assistant.AssistantFileService;
import com.orgmemory.core.assistant.AssistantFileView;
import com.orgmemory.core.assistant.AssistantPrivateFileSearch;
import com.orgmemory.core.assistant.AssistantPrivateFileSelection;
import com.orgmemory.core.assistant.AssistantService;
import com.orgmemory.core.assistant.AssistantTurn;
import com.orgmemory.core.assistant.AssistantTurnRef;
import com.orgmemory.core.ai.AssistantModelAuthorityService;
import com.orgmemory.core.ai.AssistantModelChoice;
import com.orgmemory.core.ai.AssistantModelRouteAuthority;
import com.orgmemory.core.ai.AssistantModelSelectionRef;
import com.orgmemory.core.knowledge.retrieval.CitationEvidenceReference;
import com.orgmemory.core.knowledge.retrieval.CitationEvidenceService;
import com.orgmemory.core.knowledge.search.KnowledgeEvidenceSelection;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.permission.KnowledgeClassification;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;
import tools.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/api/assistant")
class AssistantController {

    private static final String UI_MESSAGE_STREAM_HEADER = "x-vercel-ai-ui-message-stream";
    private static final String TEXT_PART_ID = "answer";
    private static final List<AssistantStarterPrompt> STARTERS = List.of(
            new AssistantStarterPrompt(
                    "people-policy",
                    "People policy",
                    "What is the probation policy?"),
            new AssistantStarterPrompt(
                    "travel-expense",
                    "Travel expenses",
                    "How do I submit a travel expense claim?"),
            new AssistantStarterPrompt(
                    "release-process",
                    "Release process",
                    "What is the product release process?"));

    private final AssistantService assistant;
    private final AssistantConversationService conversations;
    private final CurrentActorProvider actors;
    private final AssistantProperties properties;
    private final AssistantModelAuthorityService modelAuthority;
    private final CitationEvidenceService citationEvidence;
    private final AssistantRetrievalScheduler retrievalScheduler;
    private final AssistantEvidenceUploadService evidenceUploads;
    private final AssistantEvidenceService evidence;
    private final AssistantFileService assistantFiles;
    private final AssistantPrivateFileSearch privateFileSearch;
    private final ObjectMapper json;

    @org.springframework.beans.factory.annotation.Autowired
    AssistantController(
            AssistantService assistant,
            AssistantConversationService conversations,
            CurrentActorProvider actors,
            AssistantProperties properties,
            AssistantModelAuthorityService modelAuthority,
            CitationEvidenceService citationEvidence,
            AssistantRetrievalScheduler retrievalScheduler,
            AssistantEvidenceUploadService evidenceUploads,
            AssistantEvidenceService evidence,
            AssistantFileService assistantFiles,
            AssistantPrivateFileSearch privateFileSearch,
            ObjectMapper json) {
        this.assistant = assistant;
        this.conversations = conversations;
        this.actors = actors;
        this.properties = properties;
        this.modelAuthority = modelAuthority;
        this.citationEvidence = citationEvidence;
        this.retrievalScheduler = retrievalScheduler;
        this.evidenceUploads = evidenceUploads;
        this.evidence = evidence;
        this.assistantFiles = assistantFiles;
        this.privateFileSearch = privateFileSearch;
        this.json = json;
    }

    AssistantController(
            AssistantService assistant,
            AssistantConversationService conversations,
            CurrentActorProvider actors,
            AssistantProperties properties,
            AssistantModelAuthorityService modelAuthority,
            CitationEvidenceService citationEvidence,
            AssistantRetrievalScheduler retrievalScheduler,
            AssistantEvidenceUploadService evidenceUploads,
            AssistantEvidenceService evidence,
            ObjectMapper json) {
        this(
                assistant, conversations, actors, properties, modelAuthority,
                citationEvidence, retrievalScheduler, evidenceUploads, evidence,
                null, null, json);
    }

    @PostMapping(path = "/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "uploadAssistantFile", summary = "Upload one actor-private Assistant file")
    AssistantFileView uploadFile(
            @RequestPart("file") MultipartFile file,
            Authentication authentication) {
        try (var content = file.getInputStream()) {
            return assistantFiles.upload(
                    actors.current(authentication),
                    file.getOriginalFilename(),
                    file.getSize(),
                    content);
        } catch (IOException failure) {
            throw new ApiRequestException("The uploaded Assistant file could not be read", failure);
        }
    }

    @GetMapping("/files")
    @Operation(operationId = "listRecentAssistantFiles", summary = "List the current actor's recent private files")
    AssistantFilePage recentFiles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication authentication) {
        return assistantFiles.recent(actors.current(authentication), page, limit);
    }

    @GetMapping("/files/{fileId}")
    @Operation(operationId = "getAssistantFile", summary = "Read one owned private file's processing state")
    AssistantFileView file(
            @PathVariable UUID fileId,
            Authentication authentication) {
        return assistantFiles.get(actors.current(authentication), fileId);
    }

    @DeleteMapping("/files/{fileId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "deleteAssistantFile", summary = "Deny and schedule cleanup of one owned private file")
    void deleteFile(
            @PathVariable UUID fileId,
            Authentication authentication) {
        assistantFiles.delete(actors.current(authentication), fileId);
    }

    @GetMapping("/files/{fileId}/content")
    @Operation(operationId = "readAssistantFileContent", summary = "Stream one freshly owner-authorized private file")
    ResponseEntity<StreamingResponseBody> fileContent(
            @PathVariable UUID fileId,
            Authentication authentication) {
        AssistantFileContent file = assistantFiles.open(actors.current(authentication), fileId);
        StreamingResponseBody body = output -> {
            try (file) {
                file.stream().transferTo(output);
            }
        };
        ContentDisposition disposition = (file.inlinePreviewAllowed()
                        ? ContentDisposition.inline()
                        : ContentDisposition.attachment())
                .filename(file.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.mediaType()))
                .contentLength(file.contentLength())
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(body);
    }

    @GetMapping("/files/{fileId}/chunks/{chunkId}")
    @Operation(operationId = "readAssistantFileExcerpt", summary = "Read one freshly owner-authorized private citation excerpt")
    ResponseEntity<AssistantPrivateFileExcerptResponse> fileExcerpt(
            @PathVariable UUID fileId,
            @PathVariable UUID chunkId,
            @RequestParam long generation,
            Authentication authentication) {
        var citation = privateFileSearch.findCitation(
                        actors.current(authentication), fileId, generation, chunkId)
                .orElseThrow(AssistantFileService::notFound);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff")
                .body(new AssistantPrivateFileExcerptResponse(
                        citation.title(), citation.heading(), citation.startPage(),
                        citation.endPage(), citation.content()));
    }

    @PostMapping(path = "/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            operationId = "uploadAssistantEvidence",
            summary = "Upload one governed file and bind it to an Assistant conversation")
    AssistantEvidenceBindingView uploadEvidence(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) UUID conversationId,
            @RequestParam UUID knowledgeSpaceId,
            @RequestParam(defaultValue = "CONFIDENTIAL")
                    KnowledgeClassification classification,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        try (var content = file.getInputStream()) {
            return evidenceUploads.upload(
                    actor,
                    conversationId,
                    knowledgeSpaceId,
                    classification,
                    file.getOriginalFilename(),
                    file.getSize(),
                    content);
        } catch (IOException failure) {
            throw new ApiRequestException(
                    "The uploaded Assistant file could not be read",
                    failure);
        }
    }

    @GetMapping("/conversations/{conversationId}/evidence/{bindingId}")
    @Operation(
            operationId = "getAssistantEvidence",
            summary = "Read the active-engine preparation state of one owned binding")
    AssistantEvidenceBindingView evidence(
            @PathVariable UUID conversationId,
            @PathVariable UUID bindingId,
            Authentication authentication) {
        return evidence.get(
                actors.current(authentication),
                conversationId,
                bindingId);
    }

    @GetMapping("/conversations/{conversationId}/evidence")
    @Operation(
            operationId = "listAssistantEvidence",
            summary = "List governed file bindings for one owned conversation")
    List<AssistantEvidenceBindingView> evidence(
            @PathVariable UUID conversationId,
            Authentication authentication) {
        return evidence.list(
                actors.current(authentication),
                conversationId);
    }

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(operationId = "streamAssistantChat", summary = "Stream an answer from permission-verified knowledge")
    ResponseEntity<Flux<ServerSentEvent<String>>> chat(
            @Valid @RequestBody AssistantChatRequest request,
            Authentication authentication) {
        String requestId = UUID.randomUUID().toString();
        CurrentActor actor = actors.current(authentication);
        AssistantModelRouteAuthority routeAuthority = modelAuthority.authorize(
                actor.organizationId(),
                request.modelActivationId());
        AssistantModelSelectionRef modelSelection = modelAuthority.selectionRef(
                routeAuthority);
        List<UUID> requestedEvidence = request.evidenceBindingIds() == null
                ? List.of()
                : request.evidenceBindingIds();
        List<UUID> requestedPrivateFiles = request.assistantFileIds() == null
                ? List.of()
                : request.assistantFileIds();
        if (!requestedEvidence.isEmpty() && !requestedPrivateFiles.isEmpty()) {
            throw new ApiRequestException(
                    "A turn cannot mix governed Knowledge and private Assistant files");
        }
        AssistantTurnRef turnRef;
        KnowledgeEvidenceSelection knowledgeSelection = KnowledgeEvidenceSelection.unrestricted();
        AssistantPrivateFileSelection privateSelection = new AssistantPrivateFileSelection(List.of());
        if (!requestedPrivateFiles.isEmpty()) {
            var claim = conversations.beginTurnWithPrivateFiles(
                    actor,
                    request.conversationId(),
                    request.message(),
                    modelSelection,
                    requestedPrivateFiles);
            turnRef = claim.turn();
            privateSelection = claim.selection();
        } else if (!requestedEvidence.isEmpty()) {
            AssistantEvidenceTurnClaim claim = conversations.beginTurnWithEvidence(
                    actor,
                    request.conversationId(),
                    request.message(),
                    modelSelection,
                    requestedEvidence);
            turnRef = claim.turn();
            knowledgeSelection = claim.selection();
        } else {
            turnRef = conversations.beginTurn(
                    actor,
                    request.conversationId(),
                    request.message(),
                    modelSelection);
        }
        UUID conversationId = turnRef.conversationId();
        UUID assistantMessageId = UUID.randomUUID();
        KnowledgeEvidenceSelection finalKnowledgeSelection = knowledgeSelection;
        AssistantPrivateFileSelection finalPrivateSelection = privateSelection;
        Flux<AssistantStreamPart> parts = Flux.defer(() -> {
            long turnStartedAtNanos = System.nanoTime();
            return Flux.concat(
                    Flux.just(
                            new AssistantStreamPart.StartStep(),
                            new AssistantStreamPart.Activity(
                                    AssistantStreamPart.Activity.Phase.RETRIEVAL,
                                    AssistantStreamPart.Activity.State.ACTIVE,
                                    null)),
                    retrievalScheduler.schedule(() -> finalPrivateSelection.restricted()
                                    ? assistant.startTurnWithPrivateFiles(
                                            actor,
                                            request.message(),
                                            request.limit(),
                                            requestId,
                                            conversationId.toString(),
                                            routeAuthority,
                                            turnStartedAtNanos,
                                            finalPrivateSelection)
                                    : assistant.startTurn(
                                            actor,
                                            request.message(),
                                            request.limit(),
                                            requestId,
                                            conversationId.toString(),
                                            routeAuthority,
                                            turnStartedAtNanos,
                                            finalKnowledgeSelection))
                            .flatMapMany(turn -> completedTurnParts(
                                    actor,
                                    turnRef,
                                    assistantMessageId,
                                    turn)));
        });
        return ResponseEntity.ok()
                .header("X-Request-ID", requestId)
                .header("X-Conversation-ID", conversationId.toString())
                .header(UI_MESSAGE_STREAM_HEADER, "v1")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform")
                .header(HttpHeaders.CONNECTION, "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(UiMessageStream.encode(
                        parts,
                        assistantMessageId,
                        json,
                        properties.heartbeatInterval(),
                        properties.turnTimeout()));
    }

    record RenameConversationRequest(
            @NotBlank @Size(max = 120) String title) {
    }

    record AnswerFeedbackRequest(@NotNull AssistantAnswerSentiment sentiment) {
    }

    record AssistantPrivateFileExcerptResponse(
            String title,
            String heading,
            Integer startPage,
            Integer endPage,
            String content) {}

    record AssistantStarterPrompt(String id, String label, String prompt) {
    }

    record AssistantModelOptionResponse(
            UUID id,
            String gatewayLabel,
            String provider,
            String modelId,
            String displayName,
            boolean defaultChoice) {

        static AssistantModelOptionResponse from(AssistantModelChoice choice) {
            return new AssistantModelOptionResponse(
                    choice.activationId(),
                    choice.gatewayLabel(),
                    choice.provider(),
                    choice.modelId(),
                    choice.displayName(),
                    choice.defaultChoice());
        }
    }

    record AssistantModelOptionsResponse(
            UUID selectedModelActivationId,
            List<AssistantModelOptionResponse> options) {
    }

    record SelectAssistantModelRequest(UUID modelActivationId) {
    }

    record AssistantCitationResponse(
            int citationNumber,
            String sourceId,
            String title,
            String heading,
            Integer startPage,
            Integer endPage,
            boolean available,
            String excerptUrl,
            String contentUrl) {
    }

    @GetMapping("/starters")
    @Operation(
            operationId = "listAssistantStarters",
            summary = "List supported prompts for starting an Assistant conversation")
    List<AssistantStarterPrompt> starters() {
        return STARTERS;
    }

    @GetMapping("/model-options")
    @Operation(
            operationId = "getAssistantModelOptions",
            summary = "List server-governed Assistant model choices for the current route")
    AssistantModelOptionsResponse modelOptions(
            @RequestParam(required = false) UUID conversationId,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        AssistantModelSelectionRef selected = conversationId == null
                ? null
                : conversations.modelSelection(actor, conversationId);
        UUID selectedActivationId = modelAuthority.resolveSelectedActivation(
                actor.organizationId(),
                selected);
        return new AssistantModelOptionsResponse(
                selectedActivationId,
                modelAuthority.choices(actor.organizationId()).stream()
                        .map(AssistantModelOptionResponse::from)
                        .toList());
    }

    @PutMapping("/conversations/{conversationId}/model")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "selectAssistantConversationModel",
            summary = "Select one allowed model for an owned Assistant conversation")
    void selectModel(
            @PathVariable UUID conversationId,
            @RequestBody SelectAssistantModelRequest request,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        UUID activationId = request == null ? null : request.modelActivationId();
        AssistantModelRouteAuthority authority = modelAuthority.authorize(
                actor.organizationId(),
                activationId);
        conversations.selectModel(
                actor,
                conversationId,
                modelAuthority.selectionRef(authority));
    }

    @PutMapping("/messages/{messageId}/feedback")
    @Operation(
            operationId = "setAssistantAnswerFeedback",
            summary = "Create or replace feedback on an owned Assistant answer")
    AssistantAnswerFeedbackView setFeedback(
            @PathVariable UUID messageId,
            @Valid @RequestBody AnswerFeedbackRequest request,
            Authentication authentication) {
        return conversations.setAnswerFeedback(
                actors.current(authentication), messageId, request.sentiment());
    }

    @DeleteMapping("/messages/{messageId}/feedback")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            operationId = "deleteAssistantAnswerFeedback",
            summary = "Remove feedback from an owned Assistant answer")
    void deleteFeedback(
            @PathVariable UUID messageId,
            Authentication authentication) {
        conversations.deleteAnswerFeedback(
                actors.current(authentication), messageId);
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

    @GetMapping("/messages/{messageId}/citations")
    @Operation(
            operationId = "getAssistantMessageCitations",
            summary = "Hydrate currently authorized citations for one owned Assistant answer")
    ResponseEntity<List<AssistantCitationResponse>> citations(
            @PathVariable UUID messageId,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        var stored = conversations.citationReferences(actor, messageId);
        if (stored.isEmpty()) {
            return citationHydrationResponse(List.of(), UUID.randomUUID().toString());
        }
        String requestId = UUID.randomUUID().toString();
        Map<UUID, CitationEvidenceReference> visible = citationEvidence.hydrate(
                        actor,
                        stored.stream()
                                .filter(reference -> reference.kind()
                                        == AssistantCitationEvidence.Kind.KNOWLEDGE)
                                .map(com.orgmemory.core.assistant.AssistantCitationReference::chunkId)
                                .distinct()
                                .toList(),
                        requestId)
                .stream()
                .collect(Collectors.toMap(
                        CitationEvidenceReference::chunkId,
                        Function.identity()));
        List<AssistantCitationResponse> response = stored.stream()
                .map(reference -> {
                    if (reference.kind() == AssistantCitationEvidence.Kind.ASSISTANT_FILE) {
                        return privateFileSearch.findCitation(
                                        actor,
                                        reference.assistantFileId(),
                                        reference.processingGeneration(),
                                        reference.chunkId())
                                .map(privateCitation -> {
                                    String chunkId = privateCitation.chunkId().toString();
                                    String fileId = privateCitation.fileId().toString();
                                    return new AssistantCitationResponse(
                                            reference.citationNumber(),
                                            "urn:orgmemory:assistant-file-citation:"
                                                    + reference.citationNumber() + ":" + chunkId,
                                            privateCitation.title(),
                                            privateCitation.heading(),
                                            privateCitation.startPage(),
                                            privateCitation.endPage(),
                                            true,
                                            "/api/assistant/files/" + fileId + "/chunks/" + chunkId
                                                    + "?generation=" + privateCitation.processingGeneration(),
                                            "/api/assistant/files/" + fileId + "/content");
                                })
                                .orElseGet(() -> new AssistantCitationResponse(
                                        reference.citationNumber(),
                                        "urn:orgmemory:assistant-file-citation:"
                                                + reference.citationNumber() + ":" + reference.chunkId(),
                                        "Private file no longer available",
                                        null,
                                        null,
                                        null,
                                        false,
                                        null,
                                        null));
                    }
                    CitationEvidenceReference evidence = visible.get(reference.chunkId());
                    if (evidence == null) {
                        return null;
                    }
                    String chunkId = evidence.chunkId().toString();
                    return new AssistantCitationResponse(
                            reference.citationNumber(),
                            "urn:orgmemory:citation:"
                                    + reference.citationNumber()
                                    + ":"
                                    + chunkId,
                            evidence.title(),
                            evidence.heading(),
                            evidence.startPage(),
                            evidence.endPage(),
                            true,
                            "/api/citations/" + chunkId + "/excerpt",
                            "/api/citations/" + chunkId + "/content");
                })
                .filter(java.util.Objects::nonNull)
                .toList();
        return citationHydrationResponse(response, requestId);
    }

    private static ResponseEntity<List<AssistantCitationResponse>> citationHydrationResponse(
            List<AssistantCitationResponse> citations,
            String requestId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Request-ID", requestId)
                .body(citations);
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
            summary = "Delete the current actor's conversation transcript")
    void delete(
            @PathVariable UUID conversationId,
            Authentication authentication) {
        CurrentActor actor = actors.current(authentication);
        conversations.delete(actor, conversationId);
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
        Flux<AssistantStreamPart> generation = turn.citations().isEmpty()
                ? Flux.empty()
                : Flux.just(new AssistantStreamPart.Activity(
                        AssistantStreamPart.Activity.Phase.GENERATION,
                        AssistantStreamPart.Activity.State.ACTIVE,
                        null));
        Flux<AssistantStreamPart> live = Flux.merge(
                turn.activities().map(AssistantController::activityPart),
                text);
        return Flux.concat(
                Flux.just(new AssistantStreamPart.Activity(
                        AssistantStreamPart.Activity.Phase.RETRIEVAL,
                        AssistantStreamPart.Activity.State.COMPLETE,
                        turn.citations().size())),
                generation,
                Flux.fromIterable(turn.citations())
                        .map(AssistantController::sourcePart),
                live,
                Flux.just(new AssistantStreamPart.FinishStep()));
    }

    private Flux<AssistantStreamPart> completedTurnParts(
            CurrentActor actor,
            AssistantTurnRef turnRef,
            UUID assistantMessageId,
            AssistantTurn turn) {
        StringBuilder completedAnswer = new StringBuilder();
        return parts(turn)
                .doOnNext(part -> {
                    if (part instanceof AssistantStreamPart.TextDelta delta) {
                        completedAnswer.append(delta.delta());
                    }
                })
                .doOnComplete(() -> conversations.completeTurn(
                        actor,
                        turnRef,
                        assistantMessageId,
                        completedAnswer.toString(),
                        turn.citations()));
    }

    private static AssistantStreamPart sourcePart(AssistantCitation citation) {
        var evidence = citation.citationEvidence();
        boolean privateFile = evidence.kind() == AssistantCitationEvidence.Kind.ASSISTANT_FILE;
        String sourceId = (privateFile
                        ? "urn:orgmemory:assistant-file-citation:"
                        : "urn:orgmemory:citation:")
                + citation.number() + ":" + evidence.chunkId();
        String title = evidence.startPage() == null
                ? evidence.title()
                : evidence.title() + " · page " + evidence.startPage();
        return new AssistantStreamPart.SourceUrl(
                sourceId,
                privateFile
                        ? "/api/assistant/files/" + evidence.assistantFileId() + "/content"
                        : "/api/citations/" + evidence.chunkId() + "/content",
                title,
                citation.number());
    }

    private static AssistantStreamPart activityPart(
            AssistantAgentActivity activity) {
        return new AssistantStreamPart.Activity(
                AssistantStreamPart.Activity.Phase.valueOf(activity.phase().name()),
                AssistantStreamPart.Activity.State.valueOf(activity.state().name()),
                activity.resultCount(),
                activity.skillOrdinal(),
                activity.skillTitle());
    }
}
