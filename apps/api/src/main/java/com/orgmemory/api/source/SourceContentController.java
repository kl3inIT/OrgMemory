package com.orgmemory.api.source;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.retrieval.SourceContentService;
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
@RequestMapping("/api/sources")
class SourceContentController {

    private final SourceContentService content;
    private final CurrentActorProvider actors;

    SourceContentController(SourceContentService content, CurrentActorProvider actors) {
        this.content = content;
        this.actors = actors;
    }

    @GetMapping("/{sourceId}/content")
    @Operation(operationId = "readSourceContent", summary = "Stream permission-verified current source evidence")
    ResponseEntity<StreamingResponseBody> content(
            @PathVariable UUID sourceId,
            Authentication authentication) {
        String requestId = UUID.randomUUID().toString();
        var source = content.open(actors.current(authentication), sourceId, requestId);
        StreamingResponseBody body = output -> {
            try (source) {
                source.stream().transferTo(output);
            }
        };
        SafeRepresentation representation = safeRepresentation(source.fileName());
        return ResponseEntity.ok()
                .contentType(representation.mediaType())
                .contentLength(source.contentLength())
                .cacheControl(CacheControl.noStore())
                .header("X-Request-ID", requestId)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        representation.contentDisposition()
                                .filename(source.fileName(), java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private static SafeRepresentation safeRepresentation(String fileName) {
        return KnowledgeContentType.fromFileName(fileName)
                .map(contentType -> new SafeRepresentation(
                        MediaType.parseMediaType(contentType.browserSafeMediaType()),
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
