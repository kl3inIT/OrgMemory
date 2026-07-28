package com.orgmemory.api.assetregistry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.AssetRegistryService;
import com.orgmemory.core.assetregistry.CapabilityPackService;
import com.orgmemory.core.assetregistry.PromptExecutionService;
import com.orgmemory.core.assetregistry.SkillDistributionService;
import com.orgmemory.core.assetregistry.SkillInstallManifest;
import com.orgmemory.core.assetregistry.WorkInstructionService;
import com.orgmemory.core.organization.CurrentActor;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;

class AssetConsumptionControllerTests {

    private static final UUID ASSET_ID =
            UUID.fromString("86000000-0000-0000-0000-000000000003");
    private static final UUID RELEASE_ID =
            UUID.fromString("86000000-0000-0000-0000-000000000004");
    private static final CurrentActor ACTOR = new CurrentActor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "Browser user",
            "browser.user@example.test");

    @Test
    void browserSessionReadsTheExactSkillInstallContract() {
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        SkillDistributionService skills =
                mock(SkillDistributionService.class);
        OAuth2AuthenticationToken authentication = browserSession();
        SkillInstallManifest manifest = manifest();
        when(actors.current(authentication)).thenReturn(ACTOR);
        when(skills.manifest(ACTOR, ASSET_ID, RELEASE_ID))
                .thenReturn(manifest);
        AssetConsumptionController controller =
                controller(actors, skills);

        var response = controller.getSkillInstallContract(
                ASSET_ID, RELEASE_ID, authentication);

        assertSame(manifest, response.getBody());
        assertEquals(
                CacheControl.noStore().getHeaderValue(),
                response.getHeaders().getCacheControl());
        verify(skills).manifest(ACTOR, ASSET_ID, RELEASE_ID);
    }

    @Test
    void bearerTokenCannotBypassDeliveryScopeThroughTheBrowserEndpoint() {
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        SkillDistributionService skills =
                mock(SkillDistributionService.class);
        AssetConsumptionController controller =
                controller(actors, skills);
        var authentication = new TestingAuthenticationToken(
                "actor",
                "token",
                new SimpleGrantedAuthority("SCOPE_profile"));

        assertThrows(
                AccessDeniedException.class,
                () -> controller.getSkillInstallContract(
                        ASSET_ID, RELEASE_ID, authentication));

        verifyNoInteractions(actors, skills);
    }

    private static AssetConsumptionController controller(
            CurrentActorProvider actors,
            SkillDistributionService skills) {
        return new AssetConsumptionController(
                actors,
                mock(AssetRegistryService.class),
                mock(PromptExecutionService.class),
                mock(WorkInstructionService.class),
                mock(CapabilityPackService.class),
                skills);
    }

    private static OAuth2AuthenticationToken browserSession() {
        Instant issuedAt = Instant.now();
        var authorities =
                List.of(new SimpleGrantedAuthority("OIDC_USER"));
        var idToken = new OidcIdToken(
                "test-id-token",
                issuedAt,
                issuedAt.plusSeconds(300),
                Map.of(
                        "sub", "browser-user",
                        "email", "browser.user@example.test"));
        var user = new DefaultOidcUser(authorities, idToken);
        return new OAuth2AuthenticationToken(
                user, authorities, "keycloak");
    }

    private static SkillInstallManifest manifest() {
        return new SkillInstallManifest(
                ASSET_ID,
                RELEASE_ID,
                "productivity",
                "decision-record-writer",
                "productivity/decision-record-writer",
                "1.0.0",
                "Decision record writer",
                "Write a concise decision record",
                "a".repeat(64),
                "b".repeat(64),
                1024,
                "application/zip",
                "MIT",
                "Claude Code and Codex",
                "Read",
                Map.of(),
                List.of(new SkillInstallManifest.File(
                        "SKILL.md",
                        512,
                        "c".repeat(64))));
    }
}
