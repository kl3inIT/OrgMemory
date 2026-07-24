package com.orgmemory.api.knowledge;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.CitationContentService;
import io.swagger.v3.oas.annotations.Operation;
import java.nio.file.Path;
import java.util.Locale;
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
        MediaType responseMediaType = safeMediaType(
                citation.fileName());
        return ResponseEntity.ok()
                .contentType(responseMediaType)
                .contentLength(citation.contentLength())
                .cacheControl(CacheControl.noStore())
                .header("X-Request-ID", requestId)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        contentDisposition(responseMediaType)
                                .filename(
                                        citation.fileName(),
                                        java.nio.charset.StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    private static MediaType safeMediaType(String fileName) {
        String normalized = Path.of(fileName)
                .getFileName()
                .toString();
        int separator = normalized.lastIndexOf('.');
        String extension = separator < 0
                ? ""
                : normalized.substring(separator + 1)
                        .toLowerCase(Locale.ROOT);
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "txt", "md" -> MediaType.TEXT_PLAIN;
            case "png" -> MediaType.IMAGE_PNG;
            case "jpg", "jpeg" -> MediaType.IMAGE_JPEG;
            case "gif" -> MediaType.IMAGE_GIF;
            case "webp" -> MediaType.parseMediaType("image/webp");
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
            case "pptx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private static ContentDisposition.Builder contentDisposition(
            MediaType mediaType) {
        if (MediaType.APPLICATION_PDF.equals(mediaType)
                || MediaType.TEXT_PLAIN.equals(mediaType)
                || "image".equals(mediaType.getType())) {
            return ContentDisposition.inline();
        }
        return ContentDisposition.attachment();
    }
}
