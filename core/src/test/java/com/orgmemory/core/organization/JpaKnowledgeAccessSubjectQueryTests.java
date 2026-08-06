package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JpaKnowledgeAccessSubjectQueryTests {

    private static final UUID ORGANIZATION_ID = UUID.randomUUID();
    private static final UUID DEPARTMENT_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();

    private final AppUserRepository users = mock(AppUserRepository.class);
    private final JpaKnowledgeAccessSubjectQuery query =
            new JpaKnowledgeAccessSubjectQuery(users);

    @Test
    void activeExecutiveUsesCanonicalPersistedDepartmentAndRole() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(
                ORGANIZATION_ID,
                Clearance.EXECUTIVE)));

        assertEquals(
                Optional.of(new KnowledgeAccessSubject(
                        USER_ID,
                        ORGANIZATION_ID,
                        DEPARTMENT_ID,
                        true)),
                query.findActive(ORGANIZATION_ID, USER_ID));
    }

    @Test
    void platformAdminDoesNotBecomeKnowledgeExecutive() {
        when(users.findById(USER_ID)).thenReturn(Optional.of(user(
                ORGANIZATION_ID,
                Clearance.STANDARD)));

        KnowledgeAccessSubject subject = query.findActive(
                        ORGANIZATION_ID,
                        USER_ID)
                .orElseThrow();

        assertFalse(subject.executive());
    }

    @Test
    void inactiveOrForeignTenantSubjectsFailClosed() {
        AppUser inactive = user(ORGANIZATION_ID, Clearance.STANDARD);
        inactive.deactivate();
        when(users.findById(USER_ID)).thenReturn(Optional.of(inactive));

        assertTrue(query.findActive(ORGANIZATION_ID, USER_ID).isEmpty());

        when(users.findById(USER_ID)).thenReturn(Optional.of(user(
                UUID.randomUUID(),
                Clearance.STANDARD)));

        assertTrue(query.findActive(ORGANIZATION_ID, USER_ID).isEmpty());
    }

    private static AppUser user(UUID organizationId, Clearance clearance) {
        return new AppUser(
                organizationId,
                DEPARTMENT_ID,
                "Nguyen Van An",
                "an@example.test",
                clearance);
    }
}
