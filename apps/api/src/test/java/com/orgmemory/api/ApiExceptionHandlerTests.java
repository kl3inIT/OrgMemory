package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.core.knowledge.CitationNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionHandlerTests {

    @Test
    void missingAndUnauthorizedCitationsShareTheSameOpaqueResponse() {
        var response = new ApiExceptionHandler()
                .citationNotFound(new CitationNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus());
        assertEquals(
                "The requested citation is not available",
                response.getDetail());
    }
}
