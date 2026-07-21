package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;

class BrowserLoginFlowTests {

    private final BrowserLoginFlow flow = new BrowserLoginFlow(new OrgMemoryOidcProperties(
            URI.create("http://localhost:8180/realms/orgmemory"),
            "orgmemory-web",
            "test-secret",
            URI.create("http://localhost:5173")));

    @Test
    void preservesAValidatedLocalReturnPathAcrossLogin() throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        flow.rememberReturnPath(request, "/ask?q=leave-policy#answer");

        flow.onAuthenticationSuccess(request, response, mock(Authentication.class));

        assertEquals("http://localhost:5173/ask?q=leave-policy#answer", response.getRedirectedUrl());
        var session = request.getSession(false);
        assertNotNull(session);
        assertNull(session.getAttribute(BrowserLoginFlow.RETURN_TO_SESSION_ATTRIBUTE));
    }

    @Test
    void rejectsExternalOrAmbiguousReturnTargets() {
        assertEquals("/", BrowserLoginFlow.safeReturnPath("https://attacker.example/path"));
        assertEquals("/", BrowserLoginFlow.safeReturnPath("//attacker.example/path"));
        assertEquals("/", BrowserLoginFlow.safeReturnPath("/\\attacker.example/path"));
        assertEquals("/", BrowserLoginFlow.safeReturnPath("/%5c%5cattacker.example/path"));
        assertEquals("/", BrowserLoginFlow.safeReturnPath("/ask\r\nLocation: https://attacker.example"));
    }

    @Test
    void keepsTheOriginalReturnPathWhenRetryingAfterAnAuthenticationFailure() throws Exception {
        var request = new MockHttpServletRequest();
        flow.rememberReturnPath(request, "/review");
        flow.rememberReturnPath(request, null);
        var response = new MockHttpServletResponse();

        flow.onAuthenticationSuccess(request, response, mock(Authentication.class));

        assertEquals("http://localhost:5173/review", response.getRedirectedUrl());
    }
}
