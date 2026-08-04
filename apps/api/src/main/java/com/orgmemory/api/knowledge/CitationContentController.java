package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.retrieval.CitationContentService;
import com.orgmemory.core.knowledge.retrieval.CitationEvidenceExcerpt;
import com.orgmemory.core.knowledge.retrieval.CitationEvidenceService;
import com.orgmemory.core.knowledge.sourceledger.KnowledgeContentType;
import io.swagger.v3.oas.annotations.Operation;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/citations")
class CitationContentController {

    private final CitationContentService citations;
    private final CitationEvidenceService evidence;
    private final CurrentActorProvider actors;

    CitationContentController(
            CitationContentService citations,
            CitationEvidenceService evidence,
            CurrentActorProvider actors) {
        this.citations = citations;
        this.evidence = evidence;
        this.actors = actors;
    }

    @GetMapping("/{chunkId}/excerpt")
    @Operation(
            operationId = "readCitationExcerpt",
            summary = "Read a bounded permission-verified citation excerpt")
    ResponseEntity<CitationEvidenceExcerpt> excerpt(
            @PathVariable UUID chunkId,
            Authentication authentication) {
        String requestId = UUID.randomUUID().toString();
        CitationEvidenceExcerpt excerpt = evidence.excerpt(
                actors.current(authentication), chunkId, requestId);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Request-ID", requestId)
                .header("X-Content-Type-Options", "nosniff")
                .body(excerpt);
    }

    @GetMapping("/{chunkId}/content")
    @Operation(
            operationId = "readCitationContent",
            summary = "Stream permission-verified source evidence")
    ResponseEntity<StreamingResponseBody> content(
            @PathVariable UUID chunkId,
            Authentication authentication) {
        String requestId = UUID.randomUUID().toString();
        var citation = citations.open(
                actors.current(authentication),
                chunkId,
                requestId);
        StreamingResponseBody body = output -> {
            try (citation) {
                citation.stream().transferTo(output);
            }
        };
        SafeRepresentation representation =
                safeRepresentation(citation.fileName());
        return ResponseEntity.ok()
                .contentType(representation.mediaType())
                .contentLength(citation.contentLength())
                .cacheControl(CacheControl.noStore())
                .header("X-Request-ID", requestId)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        representation.contentDisposition()
                                .filename(
                                        citation.fileName(),
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private static SafeRepresentation safeRepresentation(String fileName) {
        return KnowledgeContentType.fromFileName(fileName)
                .map(contentType -> new SafeRepresentation(
                        MediaType.parseMediaType(
                                contentType.browserSafeMediaType()),
                        contentType.inlinePreviewAllowed()
                                ? ContentDisposition.inline()
                                : ContentDisposition.attachment()))
                .orElseGet(() -> new SafeRepresentation(
                        MediaType.APPLICATION_OCTET_STREAM,
                        ContentDisposition.attachment()));
    }

    private record SafeRepresentation(
            MediaType mediaType,
            ContentDisposition.Builder contentDisposition) {}
}
