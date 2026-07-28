package com.orgmemory.core.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.core.shared.error.BusinessNotFoundException;
import com.orgmemory.core.shared.error.BusinessValidationException;
import com.orgmemory.core.shared.secret.EncryptedSecret;
import com.orgmemory.core.shared.secret.SecretCipher;
import com.orgmemory.core.shared.secret.SecretValue;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AiGatewayAdministrationServiceTests {

    private final AiGatewayProfileRepository profiles =
            mock(AiGatewayProfileRepository.class);
    private final AiGatewayCredentialRepository credentials =
            mock(AiGatewayCredentialRepository.class);
    private final AiRouteOverrideRepository routes =
            mock(AiRouteOverrideRepository.class);
    private final AiGatewayEndpointPolicy endpoints =
            mock(AiGatewayEndpointPolicy.class);
    private final SecretCipher cipher = mock(SecretCipher.class);
    private final PermissionAuditService audit =
            mock(PermissionAuditService.class);
    private final AtomicReference<AiGatewayProfile> storedProfile =
            new AtomicReference<>();
    private final AtomicReference<AiGatewayCredential> storedCredential =
            new AtomicReference<>();
    private final AiGatewayAdministrationService service =
            new AiGatewayAdministrationService(
                    profiles,
                    credentials,
                    routes,
                    endpoints,
                    cipher,
                    audit);

    @BeforeEach
    void storeRepositoryArguments() {
        when(profiles.save(any())).thenAnswer(invocation -> {
            AiGatewayProfile profile = invocation.getArgument(0);
            storedProfile.set(profile);
            return profile;
        });
        when(credentials.save(any())).thenAnswer(invocation -> {
            AiGatewayCredential credential = invocation.getArgument(0);
            storedCredential.set(credential);
            return credential;
        });
        when(credentials.findByOrganizationIdAndGatewayProfileId(
                        any(),
                        any()))
                .thenAnswer(invocation -> Optional.ofNullable(
                        storedCredential.get()));
        when(endpoints.requireAllowed(
                        AiGatewayPreset.OPENAI,
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        "https://api.openai.com/v1"))
                .thenReturn("https://api.openai.com/v1");
        when(cipher.encrypt(any()))
                .thenReturn(new EncryptedSecret(
                        "cipher-text-only",
                        1));
        when(cipher.decrypt(any()))
                .thenReturn(SecretValue.of("rotated-secret"));
    }

    @Test
    void storesOnlyCiphertextAndKeepsCredentialsOutOfViewsAndLogs() {
        UUID organizationId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        SecretValue supplied = SecretValue.of("plain-api-key");

        AiGatewayProfileView result = service.create(
                organizationId,
                "openai-main",
                "OpenAI",
                AiGatewayPreset.OPENAI,
                AiGatewayCategory.DIRECT_PROVIDER,
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                "https://api.openai.com/v1",
                60,
                supplied,
                adminUserId);

        assertTrue(result.credentialSet());
        assertFalse(result.toString().contains("plain-api-key"));
        assertEquals(
                "cipher-text-only",
                storedCredential.get().stored().cipherText());
        assertFalse(storedCredential.get().toString().contains("cipher-text-only"));
        assertFalse(storedCredential.get().toString().contains("plain-api-key"));
        verify(cipher).encrypt(supplied);
    }

    @Test
    void credentialRotationAlwaysAdvancesTheRuntimeCacheRevision() {
        UUID organizationId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        service.create(
                organizationId,
                "openai-main",
                "OpenAI",
                AiGatewayPreset.OPENAI,
                AiGatewayCategory.DIRECT_PROVIDER,
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                "https://api.openai.com/v1",
                60,
                SecretValue.of("first-secret"),
                adminUserId);
        when(profiles.findByIdAndOrganizationId(
                        storedProfile.get().getId(),
                        organizationId))
                .thenReturn(Optional.of(storedProfile.get()));
        when(profiles.findByOrganizationIdAndGatewayKey(
                        organizationId,
                        "openai-main"))
                .thenReturn(Optional.of(storedProfile.get()));
        long before = service.connection(
                        organizationId,
                        "openai-main")
                .orElseThrow()
                .profileVersion();

        service.setCredential(
                organizationId,
                storedProfile.get().getId(),
                SecretValue.of("second-secret"),
                adminUserId);

        long after = service.connection(
                        organizationId,
                        "openai-main")
                .orElseThrow()
                .profileVersion();
        assertNotEquals(before, after);
        assertTrue(after > before);
    }

    @Test
    void aProfileIdFromAnotherOrganizationIsOpaqueAndCannotRotateASecret() {
        UUID organizationId = UUID.randomUUID();
        UUID foreignProfileId = UUID.randomUUID();
        when(profiles.findByIdAndOrganizationId(
                        foreignProfileId,
                        organizationId))
                .thenReturn(Optional.empty());

        assertThrows(
                BusinessNotFoundException.class,
                () -> service.setCredential(
                        organizationId,
                        foreignProfileId,
                        SecretValue.of("do-not-store"),
                        UUID.randomUUID()));

        verify(credentials, never()).save(any());
        verify(cipher, never()).encrypt(eq(SecretValue.of("do-not-store")));
    }

    @Test
    void providerPresetCannotBeRelabeledAsAnotherProtocolOrCategory() {
        assertThrows(
                BusinessValidationException.class,
                () -> service.create(
                        UUID.randomUUID(),
                        "misleading-provider",
                        "Not really Anthropic",
                        AiGatewayPreset.ANTHROPIC,
                        AiGatewayCategory.GATEWAY_ROUTER,
                        AiGatewayProtocol.OPENAI_COMPATIBLE,
                        "https://api.anthropic.com",
                        60,
                        SecretValue.of("secret"),
                        UUID.randomUUID()));

        verify(credentials, never()).save(any());
    }

    @Test
    void metadataAndCredentialUpdateShareOneServiceTransaction() {
        UUID organizationId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        AiGatewayProfileView created = service.create(
                organizationId,
                "openai-main",
                "OpenAI",
                AiGatewayPreset.OPENAI,
                AiGatewayCategory.DIRECT_PROVIDER,
                AiGatewayProtocol.OPENAI_COMPATIBLE,
                "https://api.openai.com/v1",
                60,
                null,
                adminUserId);
        when(profiles.findByIdAndOrganizationId(
                        created.id(),
                        organizationId))
                .thenReturn(Optional.of(storedProfile.get()));
        long before = created.version();

        AiGatewayProfileView updated = service.update(
                organizationId,
                created.id(),
                "Primary OpenAI",
                "https://api.openai.com/v1",
                45,
                SecretValue.of("replacement-secret"),
                adminUserId);

        assertEquals("Primary OpenAI", updated.displayName());
        assertTrue(updated.credentialSet());
        assertTrue(updated.version() > before);
        assertEquals(
                "cipher-text-only",
                storedCredential.get().stored().cipherText());
    }

    @Test
    void clearingAnEditableRouteDeletesOnlyTheOrganizationScopedOverride() {
        UUID organizationId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        AiRouteOverride override = new AiRouteOverride(
                organizationId,
                AiWorkload.ASSISTANT_CHAT,
                UUID.randomUUID(),
                "model",
                adminUserId,
                java.time.Instant.now());
        when(routes.findByOrganizationIdAndWorkload(
                        organizationId,
                        AiWorkload.ASSISTANT_CHAT))
                .thenReturn(Optional.of(override));

        service.clearRoute(
                organizationId,
                AiWorkload.ASSISTANT_CHAT,
                adminUserId);

        verify(routes).delete(override);
        assertThrows(
                BusinessValidationException.class,
                () -> service.clearRoute(
                        organizationId,
                        AiWorkload.GRAPH_EXTRACTION,
                        adminUserId));
    }
}
