package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.shared.error.BusinessConflictException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExternalIdentityBindingServiceTests {

    private static final UUID USER_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_USER_ID =
            UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final String ISSUER = "https://identity.example.test/realms/acme";
    private static final String SUBJECT = "workforce-123";

    private final ExternalIdentityRepository identities = mock(ExternalIdentityRepository.class);
    private final ExternalIdentityBindingService service =
            new ExternalIdentityBindingService(identities);

    @Test
    void identicalReplayReturnsTheExistingBinding() {
        ExternalIdentity existing = new ExternalIdentity(USER_ID, ISSUER, SUBJECT);
        when(identities.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(existing));

        assertSame(existing, service.bind(USER_ID, ISSUER, SUBJECT));
        verify(identities, never()).insertIfAbsent(any(), any(), any(), any());
    }

    @Test
    void subjectAlreadyBoundToAnotherUserFailsClosed() {
        when(identities.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.of(new ExternalIdentity(OTHER_USER_ID, ISSUER, SUBJECT)));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.bind(USER_ID, ISSUER, SUBJECT));

        assertEquals("identity.binding-subject-conflict", failure.code());
    }

    @Test
    void userAlreadyBoundToAnotherSubjectFailsClosed() {
        when(identities.findByIssuerAndSubject(ISSUER, SUBJECT)).thenReturn(Optional.empty());
        when(identities.findByAppUserIdAndIssuer(USER_ID, ISSUER))
                .thenReturn(Optional.of(new ExternalIdentity(USER_ID, ISSUER, "other-subject")));

        BusinessConflictException failure = assertThrows(
                BusinessConflictException.class,
                () -> service.bind(USER_ID, ISSUER, SUBJECT));

        assertEquals("identity.binding-user-conflict", failure.code());
    }

    @Test
    void insertIsAcceptedOnlyAfterBothWinningKeysAreReread() {
        ExternalIdentity winner = new ExternalIdentity(USER_ID, ISSUER, SUBJECT);
        when(identities.findByIssuerAndSubject(ISSUER, SUBJECT))
                .thenReturn(Optional.empty(), Optional.of(winner));
        when(identities.findByAppUserIdAndIssuer(USER_ID, ISSUER))
                .thenReturn(Optional.empty(), Optional.of(winner));

        assertSame(winner, service.bind(USER_ID, ISSUER, SUBJECT));
        verify(identities).insertIfAbsent(any(), any(), any(), any());
    }
}
