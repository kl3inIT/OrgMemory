package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.CitationContentService;
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
    private final CurrentActorProvider actors;

    CitationContentController(
            CitationContentService citations,
            CurrentActorProvider actors) {
        this.citations = citations;
        this.actors = actors;
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
        return ResponseEntity.ok()
                .contentType(safeMediaType(citation.mediaType()))
                .contentLength(citation.contentLength())
                .cacheControl(CacheControl.noStore())
                .header("X-Request-ID", requestId)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(
                                        citation.fileName(),
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private static MediaType safeMediaType(String value) {
        try {
            return MediaType.parseMediaType(value);
        } catch (IllegalArgumentException ignored) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
