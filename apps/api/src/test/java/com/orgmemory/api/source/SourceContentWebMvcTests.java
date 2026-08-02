package com.orgmemory.api.source;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.orgmemory.api.ApiExceptionHandler;
import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.knowledge.retrieval.SourceContentService;
import com.orgmemory.core.knowledge.retrieval.SourceContent;
import com.orgmemory.core.knowledge.storage.ObjectContent;
import com.orgmemory.core.knowledge.storage.ObjectKey;
import com.orgmemory.core.knowledge.storage.StoredObject;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.shared.error.KnowledgeResourceNotFoundException;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class SourceContentWebMvcTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID MISSING_SOURCE_ID = UUID.randomUUID();
    private static final UUID DENIED_SOURCE_ID = UUID.randomUUID();
    private static final CurrentActor ACTOR = new CurrentActor(
            USER_ID, ORGANIZATION_ID, null, "User", "user@example.test");

    private MockMvc mvc;
    private SourceContentService contentService;

    @BeforeEach
    void setUp() {
        contentService = mock(SourceContentService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        when(actors.current(nullable(Authentication.class))).thenReturn(ACTOR);
        when(contentService.open(eq(ACTOR), org.mockito.ArgumentMatchers.any(UUID.class), anyString()))
                .thenThrow(new KnowledgeResourceNotFoundException());
        mvc = MockMvcBuilders.standaloneSetup(new SourceContentController(contentService, actors))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void missingAndDeniedSourcesAreWireEquivalent() throws Exception {
        MvcResult missing = mvc.perform(get(
                        "/api/sources/{sourceId}/content", MISSING_SOURCE_ID))
                .andReturn();
        MvcResult denied = mvc.perform(get(
                        "/api/sources/{sourceId}/content", DENIED_SOURCE_ID))
                .andReturn();

        assertEquals(404, missing.getResponse().getStatus());
        assertEquals(missing.getResponse().getStatus(), denied.getResponse().getStatus());
        assertEquals(missing.getResponse().getContentType(), denied.getResponse().getContentType());
        assertEquals(missing.getResponse().getContentAsString(), denied.getResponse().getContentAsString());
        assertEquals(missing.getResponse().getHeaderNames(), denied.getResponse().getHeaderNames());
    }

    @Test
    void streamsMarkdownAsPlainTextWithClosedDeliveryHeaders() throws Exception {
        UUID sourceId = UUID.randomUUID();
        byte[] bytes = "# Handbook".getBytes(StandardCharsets.UTF_8);
        var object = new ObjectContent(
                new ByteArrayInputStream(bytes),
                new StoredObject(
                        new ObjectKey("org/handbook.md"),
                        bytes.length,
                        "text/markdown",
                        "sha256",
                        null,
                        null));
        when(contentService.open(eq(ACTOR), eq(sourceId), anyString())).thenReturn(
                new SourceContent(
                        sourceId,
                        "handbook.md",
                        "text/markdown",
                        bytes.length,
                        "sha256",
                        object));

        MvcResult started = mvc.perform(get("/api/sources/{sourceId}/content", sourceId))
                .andExpect(request().asyncStarted())
                .andReturn();

        mvc.perform(asyncDispatch(started))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/plain"))
                .andExpect(content().bytes(bytes))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string(
                        "Content-Disposition",
                        org.hamcrest.Matchers.startsWith("inline")));
    }
}
