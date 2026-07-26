package com.orgmemory.api;

import com.orgmemory.core.shared.error.BusinessErrorCategory;
import com.orgmemory.core.shared.error.BusinessErrorExposure;
import com.orgmemory.core.shared.error.BusinessException;
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
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates transport-neutral business failures and Spring MVC failures to
 * one stable RFC 9457 contract.
 *
 * <p>Concrete domain exception classes are intentionally absent. New use-case
 * failures extend {@link BusinessException} and carry their category, stable
 * code, and safe public detail without coupling core to HTTP.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String CODE_PROPERTY = "code";

    @ExceptionHandler(BusinessException.class)
    ProblemDetail business(BusinessException exception) {
        HttpStatus status = status(exception.category());
        ProblemDetail problem = problem(
                status,
                exception.code(),
                exception.getMessage());
        if (exception.exposure()
                == BusinessErrorExposure.OPAQUE_RESOURCE) {
            problem.setInstance(URI.create("/api/resources"));
        }
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail optimisticConflict(OptimisticLockingFailureException exception) {
        log.debug("Concurrent persistence modification", exception);
        return problem(
                HttpStatus.CONFLICT,
                "persistence.concurrent-modification",
                "The resource changed while this operation was running");
    }

    /**
     * Compatibility boundary for legacy request validators.
     *
     * <p>New use cases must throw {@link BusinessException}. The legacy message
     * is intentionally not disclosed because some IllegalArgumentExceptions
     * describe internal invariants rather than caller-safe detail.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail legacyInvalidArgument(IllegalArgumentException exception) {
        log.debug("Legacy invalid request argument", exception);
        return problem(
                HttpStatus.BAD_REQUEST,
                "request.invalid-argument",
                "The request is invalid");
    }

    @ExceptionHandler(Exception.class)
    ProblemDetail unexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal.unexpected",
                "Unexpected error");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail body = exception.getBody();
        body.setType(problemType("request.validation-failed"));
        body.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        body.setDetail("The request contains invalid fields");
        body.setProperty(CODE_PROPERTY, "request.validation-failed");
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors()
                .forEach(field -> errors.putIfAbsent(
                        field.getField(),
                        field.getDefaultMessage()));
        body.setProperty("errors", errors);
        return handleExceptionInternal(
                exception,
                body,
                headers,
                status,
                request);
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {
        if (body instanceof ProblemDetail problem) {
            if (problem.getType() == null
                    || URI.create("about:blank").equals(problem.getType())) {
                String code = "http." + statusCode.value();
                problem.setType(problemType(code));
                problem.setProperty(CODE_PROPERTY, code);
            } else if (problem.getProperties() == null
                    || !problem.getProperties().containsKey(CODE_PROPERTY)) {
                problem.setProperty(
                        CODE_PROPERTY,
                        "http." + statusCode.value());
            }
        }
        return super.createResponseEntity(
                body,
                headers,
                statusCode,
                request);
    }

    private static ProblemDetail problem(
            HttpStatus status,
            String code,
            String detail) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(problemType(code));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty(CODE_PROPERTY, code);
        return problem;
    }

    private static URI problemType(String code) {
        return URI.create("urn:orgmemory:problem:" + code);
    }

    private static HttpStatus status(BusinessErrorCategory category) {
        return switch (category) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
        };
    }
}
