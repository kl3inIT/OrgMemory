package com.orgmemory.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.orgmemory.core.assetregistry.AssetConflictException;
import com.orgmemory.core.assetregistry.AssetNotFoundException;
import com.orgmemory.core.assetregistry.AssetUnavailableException;
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

    @Test
    void assetNotFoundUsesTheOpaqueResourceResponse() {
        var response = new ApiExceptionHandler()
                .knowledgeResourceNotFound(new AssetNotFoundException());

        assertEquals(HttpStatus.NOT_FOUND.value(), response.getStatus());
        assertEquals(
                "The requested knowledge resource is not available",
                response.getDetail());
    }

    @Test
    void assetConflictMapsToConflict() {
        var response = new ApiExceptionHandler()
                .assetConflict(new AssetConflictException("changed"));

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertEquals("changed", response.getDetail());
    }

    @Test
    void assetUnavailableMapsToServiceUnavailable() {
        var response = new ApiExceptionHandler()
                .assetUnavailable(new AssetUnavailableException("retry later"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatus());
        assertEquals("retry later", response.getDetail());
    }
}
