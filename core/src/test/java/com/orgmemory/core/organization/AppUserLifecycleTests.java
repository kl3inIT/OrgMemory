package com.orgmemory.core.organization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class AppUserLifecycleTests {

    @Test
    void localSuspensionWinsOverDirectoryActivation() {
        AppUser user = user();

        user.deactivate();
        user.applyDirectoryAccess(true);

        assertFalse(user.isLocalAccessEnabled());
        assertTrue(user.getDirectoryAccessEnabled());
        assertFalse(user.isActive());
    }

    @Test
    void readinessAndDirectoryStateBothGateEffectiveAccess() {
        AppUser user = user();
        assertNull(user.getDirectoryAccessEnabled());
        assertTrue(user.isActive());

        user.markProvisioningReady(false);
        assertFalse(user.isActive());

        user.applyDirectoryAccess(false);
        user.markProvisioningReady(true);
        assertFalse(user.isActive());

        user.applyDirectoryAccess(true);
        assertTrue(user.isActive());
    }

    private static AppUser user() {
        return new AppUser(
                UUID.randomUUID(),
                null,
                "Provisioned User",
                "provisioned@example.test",
                UserRole.EMPLOYEE);
    }
}
