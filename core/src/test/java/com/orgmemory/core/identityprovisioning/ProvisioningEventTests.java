package com.orgmemory.core.identityprovisioning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProvisioningEventTests {

    @Test
    void storesOnlyAllowlistedSortedFieldNames() {
        ProvisioningEvent event = new ProvisioningEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "public-id",
                "request-1",
                ProvisioningEventOperation.PATCH,
                ProvisioningEventOutcome.SUCCEEDED,
                "UPDATED",
                List.of("userName", "active", "userName"),
                Instant.parse("2026-07-27T00:00:00Z"));

        assertEquals("active,userName", event.getChangedFields());
        assertFalse(event.getChangedFields().contains("new@example.test"));
    }

    @Test
    void refusesValuesAndUnknownPathsInAuditMetadata() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ProvisioningEvent(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        null,
                        null,
                        "request-2",
                        ProvisioningEventOperation.REPLACE,
                        ProvisioningEventOutcome.DENIED,
                        "UNSUPPORTED",
                        List.of("emails[value eq \"secret@example.test\"]"),
                        Instant.parse("2026-07-27T00:00:00Z")));
    }
}
