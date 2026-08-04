package com.orgmemory.api.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders
        .get;

import com.orgmemory.api.ApiExceptionHandler;
import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.retrieval.CitationContentService;
import com.orgmemory.core.knowledge.retrieval.CitationEvidenceService;
import com.orgmemory.core.knowledge.retrieval.CitationNotFoundException;
import com.orgmemory.core.organization.CurrentActor;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CitationContentWebMvcTests {

    private static final UUID ORGANIZATION_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000002");
    private static final UUID MISSING_CHUNK_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000003");
    private static final UUID DENIED_CHUNK_ID =
            UUID.fromString("45000000-0000-0000-0000-000000000004");
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID,
            ORGANIZATION_ID,
            null,
            "User",
            "user@example.test");

    private MockMvc mvc;
    private CitationContentService citations;
    private CurrentActorProvider actors;

    @BeforeEach
    void setUp() {
        citations = mock(CitationContentService.class);
        actors = mock(CurrentActorProvider.class);
        when(actors.current(nullable(Authentication.class)))
                .thenReturn(ACTOR);
        when(citations.open(eq(ACTOR), any(UUID.class), anyString()))
                .thenThrow(new CitationNotFoundException());
        mvc = MockMvcBuilders.standaloneSetup(
                        new CitationContentController(
                                citations,
                                mock(CitationEvidenceService.class),
                                actors))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void missingAndDeniedCitationsAreWireEquivalent() throws Exception {
        MvcResult missing = mvc.perform(get(
                        "/api/citations/{chunkId}/content",
                        MISSING_CHUNK_ID))
                .andReturn();
        MvcResult denied = mvc.perform(get(
                        "/api/citations/{chunkId}/content",
                        DENIED_CHUNK_ID))
                .andReturn();

        assertEquals(404, missing.getResponse().getStatus());
        assertEquals(
                missing.getResponse().getStatus(),
                denied.getResponse().getStatus());
        assertEquals(
                missing.getResponse().getContentType(),
                denied.getResponse().getContentType());
        assertEquals(
                missing.getResponse().getContentAsString(),
                denied.getResponse().getContentAsString());
        assertEquals(
                missing.getResponse().getHeaderNames(),
                denied.getResponse().getHeaderNames());
    }
}
