package com.orgmemory.api.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.core.organization.AppUser;
import com.orgmemory.core.organization.AppUserRepository;
import com.orgmemory.core.organization.CurrentActor;
import com.orgmemory.core.organization.UserRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.DefaultCsrfToken;

class BrowserSessionControllerTests {

    @Test
    void returnsAnAnonymousSessionWithoutResolvingAnActor() {
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication anonymous = new AnonymousAuthenticationToken(
                "test-key",
                "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));

        var response = new BrowserSessionController(
                actors, mock(BrowserLoginFlow.class), mock(AppUserRepository.class)).session(anonymous);

        assertFalse(response.authenticated());
        assertNull(response.userId());
        verifyNoInteractions(actors);
    }

    @Test
    void exposesOnlyTheCanonicalInternalActorForAnAuthenticatedSession() {
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();
        UUID departmentId = UUID.randomUUID();
        when(authentication.isAuthenticated()).thenReturn(true);
        when(actors.current(authentication)).thenReturn(new CurrentActor(
                userId,
                organizationId,
                departmentId,
                "Laura Nguyen",
                "laura@example.test"));

        AppUserRepository users = mock(AppUserRepository.class);
        AppUser user = new AppUser(organizationId, departmentId, "Laura Nguyen", "laura@example.test",
                UserRole.ADMIN);
        when(users.findById(userId)).thenReturn(Optional.of(user));

        var response = new BrowserSessionController(
                actors, mock(BrowserLoginFlow.class), users).session(authentication);

        assertTrue(response.authenticated());
        assertEquals(userId, response.userId());
        assertEquals(organizationId, response.organizationId());
        assertEquals(departmentId, response.departmentId());
        assertEquals("Laura Nguyen", response.name());
        assertEquals(UserRole.ADMIN, response.role());
    }

    @Test
    void returnsTheServerIssuedCsrfContract() {
        var token = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "test-token");

        var response = new BrowserSessionController(
                mock(CurrentActorProvider.class), mock(BrowserLoginFlow.class), mock(AppUserRepository.class))
                .csrf(token);

        assertEquals("X-XSRF-TOKEN", response.headerName());
        assertEquals("_csrf", response.parameterName());
        assertEquals("test-token", response.token());
    }

    @Test
    void beginsLoginThroughTheServerOwnedReturnPathFlow() {
        BrowserLoginFlow loginFlow = mock(BrowserLoginFlow.class);
        var request = new MockHttpServletRequest();

        var view = new BrowserSessionController(
                mock(CurrentActorProvider.class), loginFlow, mock(AppUserRepository.class))
                .login("/ask?q=leave", request);

        assertEquals("/oauth2/authorization/keycloak", view.getUrl());
        verify(loginFlow).rememberReturnPath(request, "/ask?q=leave");
    }
}
