package com.orgmemory.api.identityprovisioning;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.orgmemory.core.identityprovisioning.ProvisioningLedgerService;
import com.orgmemory.core.identityprovisioning.ProvisioningNotFoundException;
import com.orgmemory.core.identityprovisioning.ProvisioningOperationalState;
import com.orgmemory.core.identityprovisioning.ProvisioningProviderProfile;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ProvisioningLedgerIntegrationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("pgvector/pgvector:pg18");

    private static final UUID ORGANIZATION_A =
            UUID.fromString("91000000-0000-4000-8000-000000000001");
    private static final UUID ORGANIZATION_B =
            UUID.fromString("92000000-0000-4000-8000-000000000001");

    @Autowired
    ProvisioningLedgerService ledger;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void seedOrganizations() {
        jdbc.update("DELETE FROM provisioning_connections");
        insertOrganization(ORGANIZATION_A, "Provisioning A");
        insertOrganization(ORGANIZATION_B, "Provisioning B");
    }

    @Test
    void scopedLookupAndVersionedCompareAndSetDoNotCrossTenants() {
        var connection = ledger.createDisabledConnection(
                ORGANIZATION_A,
                "workforce",
                ProvisioningProviderProfile.GENERIC_SCIM,
                true,
                false);

        assertThrows(
                ProvisioningNotFoundException.class,
                () -> ledger.requireConnection(ORGANIZATION_B, connection.id()));
        assertTrue(ledger.compareAndSetOperationalState(
                ORGANIZATION_A,
                connection.id(),
                connection.version(),
                ProvisioningOperationalState.DISABLED,
                ProvisioningOperationalState.VALIDATING));
        assertFalse(ledger.compareAndSetOperationalState(
                ORGANIZATION_A,
                connection.id(),
                connection.version(),
                ProvisioningOperationalState.DISABLED,
                ProvisioningOperationalState.VALIDATING));
        assertFalse(ledger.compareAndSetOperationalState(
                ORGANIZATION_B,
                connection.id(),
                connection.version() + 1,
                ProvisioningOperationalState.VALIDATING,
                ProvisioningOperationalState.ENABLED));
    }

    @Test
    void databaseRejectsSecondCorrelationActiveConnection() {
        var first = ledger.createDisabledConnection(
                ORGANIZATION_A,
                "primary",
                ProvisioningProviderProfile.MICROSOFT_ENTRA,
                true,
                true);
        var second = ledger.createDisabledConnection(
                ORGANIZATION_A,
                "secondary",
                ProvisioningProviderProfile.OKTA,
                true,
                true);

        assertTrue(ledger.compareAndSetOperationalState(
                ORGANIZATION_A,
                first.id(),
                first.version(),
                ProvisioningOperationalState.DISABLED,
                ProvisioningOperationalState.VALIDATING));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> ledger.compareAndSetOperationalState(
                        ORGANIZATION_A,
                        second.id(),
                        second.version(),
                        ProvisioningOperationalState.DISABLED,
                        ProvisioningOperationalState.VALIDATING));
    }

    private void insertOrganization(UUID id, String name) {
        jdbc.update(
                """
                INSERT INTO organizations (id, name, created_at, updated_at, version)
                VALUES (?, ?, now(), now(), 0)
                ON CONFLICT (id) DO NOTHING
                """,
                id,
                name);
    }
}
