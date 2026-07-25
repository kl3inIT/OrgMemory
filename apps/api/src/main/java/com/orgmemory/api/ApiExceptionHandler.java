package com.orgmemory.api;

import com.orgmemory.core.assistant.AssistantConversationNotFoundException;
import com.orgmemory.core.assistant.AssistantUnavailableException;
import com.orgmemory.core.assetregistry.AssetConflictException;
import com.orgmemory.core.assetregistry.AssetNotFoundException;
import com.orgmemory.core.assetregistry.AssetUnavailableException;
import com.orgmemory.core.knowledge.CitationNotFoundException;
import com.orgmemory.core.knowledge.KnowledgeAssetNotFoundException;
import com.orgmemory.core.knowledge.KnowledgeRetrievalUnavailableException;
import com.orgmemory.core.knowledge.KnowledgeResourceNotFoundException;
import com.orgmemory.core.knowledge.KnowledgeSpaceKeyConflictException;
import com.orgmemory.core.knowledge.KnowledgeSpaceUnavailableException;
import com.orgmemory.core.knowledge.UnsupportedConnectorSourceException;
import com.orgmemory.core.organization.OrgMemoryAccessDeniedException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final URI OPAQUE_CITATION_INSTANCE =
            URI.create("/api/citations");
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(OrgMemoryAccessDeniedException.class)
    ProblemDetail accessDenied(OrgMemoryAccessDeniedException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, e.getMessage());
    }

    @ExceptionHandler(CitationNotFoundException.class)
    ProblemDetail citationNotFound(CitationNotFoundException e) {
        ProblemDetail detail =
                ProblemDetail.forStatusAndDetail(
                        HttpStatus.NOT_FOUND,
                        e.getMessage());
        detail.setInstance(OPAQUE_CITATION_INSTANCE);
        return detail;
    }

    @ExceptionHandler({
        AssistantConversationNotFoundException.class,
        AssetNotFoundException.class,
        KnowledgeAssetNotFoundException.class,
        KnowledgeResourceNotFoundException.class
    })
    ProblemDetail knowledgeResourceNotFound(RuntimeException e) {
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "The requested knowledge resource is not available");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    /**
     * Naming a source no adapter provides is a request error, not a server fault: the caller
     * asked about something this deployment does not have.
     */
    @ExceptionHandler(UnsupportedConnectorSourceException.class)
    ProblemDetail unsupportedSource(UnsupportedConnectorSourceException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail conflict(OptimisticLockingFailureException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AssetConflictException.class)
    ProblemDetail assetConflict(AssetConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    /**
     * Two Knowledge Space names that derive the same key are usually the same space twice, so this
     * names the key that is already taken rather than inventing a suffix the creator did not ask
     * for and would have to discover afterwards.
     */
    @ExceptionHandler(KnowledgeSpaceKeyConflictException.class)
    ProblemDetail knowledgeSpaceKeyConflict(KnowledgeSpaceKeyConflictException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(KnowledgeRetrievalUnavailableException.class)
    ProblemDetail retrievalUnavailable(KnowledgeRetrievalUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(KnowledgeSpaceUnavailableException.class)
    ProblemDetail knowledgeSpaceUnavailable(KnowledgeSpaceUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(AssistantUnavailableException.class)
    ProblemDetail assistantUnavailable(AssistantUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(AssetUnavailableException.class)
    ProblemDetail assetUnavailable(AssetUnavailableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    ProblemDetail statusException(ResponseStatusException e) {
        ProblemDetail body = e.getBody();
        if (body.getDetail() == null && e.getReason() != null) {
            body.setDetail(e.getReason());
        }
        return body;
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception e) {
        log.error("Unhandled exception", e);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail body = ex.getBody();
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(field -> errors.putIfAbsent(field.getField(), field.getDefaultMessage()));
        body.setProperty("errors", errors);
        return handleExceptionInternal(ex, body, headers, status, request);
    }
}
