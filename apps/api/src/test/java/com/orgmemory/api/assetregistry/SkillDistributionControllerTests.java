package com.orgmemory.api.assetregistry;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.api.security.CurrentActorProvider;
import com.orgmemory.core.assetregistry.AssetDeliveryService;
import com.orgmemory.core.assetregistry.consumption.AssetPublicationMode;
import com.orgmemory.core.assetregistry.prompt.PromptExecutionService;
import com.orgmemory.core.assetregistry.SkillDistributionService;
import com.orgmemory.core.assetregistry.SkillInstallManifest;
import com.orgmemory.core.assetregistry.SkillPackageContent;
import com.orgmemory.core.organization.CurrentActor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;

class SkillDistributionControllerTests {

    private static final UUID ASSET_ID =
            UUID.fromString("85000000-0000-0000-0000-000000000003");
    private static final UUID RELEASE_ID =
            UUID.fromString("85000000-0000-0000-0000-000000000004");
    private static final CurrentActor ACTOR = new CurrentActor(
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "Skill user",
            "skill.user@example.test");

    @Test
    void streamsTheVerifiedPackageWithoutExposingItsStorageReference()
            throws Exception {
        byte[] bytes = new byte[] {1, 2, 3};
        TrackingInputStream stream =
                new TrackingInputStream(bytes);
        SkillInstallManifest manifest = manifest(bytes.length);
        SkillDistributionService skills =
                mock(SkillDistributionService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        when(actors.current(authentication)).thenReturn(ACTOR);
        when(skills.open(ACTOR, ASSET_ID, RELEASE_ID))
                .thenReturn(new SkillPackageContent(
                        manifest,
                        "support-triage-1.0.0.zip",
                        stream));
        AssetDeliveryController controller = new AssetDeliveryController(
                mock(AssetDeliveryService.class),
                mock(PromptExecutionService.class),
                skills,
                actors);

        var response = controller.skillPackage(
                ASSET_ID, RELEASE_ID, authentication);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertArrayEquals(bytes, output.toByteArray());
        assertEquals(bytes.length, response.getHeaders().getContentLength());
        assertEquals(
                CacheControl.noStore().getHeaderValue(),
                response.getHeaders().getCacheControl());
        assertTrue(response.getHeaders()
                .getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .contains("support-triage-1.0.0.zip"));
        assertEquals(
                "\"" + "a".repeat(64) + "\"",
                response.getHeaders().getETag());
        assertFalse(response.getHeaders().toString()
                .contains("private/skill.zip"));
        assertTrue(stream.closed);
    }

    @Test
    void closesThePackageIfResponseMetadataCannotBeBuilt() {
        TrackingInputStream stream =
                new TrackingInputStream(new byte[] {1});
        SkillDistributionService skills =
                mock(SkillDistributionService.class);
        CurrentActorProvider actors = mock(CurrentActorProvider.class);
        Authentication authentication = mock(Authentication.class);
        when(actors.current(authentication)).thenReturn(ACTOR);
        when(skills.open(ACTOR, ASSET_ID, RELEASE_ID))
                .thenReturn(new SkillPackageContent(
                        manifest(1, "invalid media type;"),
                        "support-triage-1.0.0.zip",
                        stream));
        AssetDeliveryController controller = new AssetDeliveryController(
                mock(AssetDeliveryService.class),
                mock(PromptExecutionService.class),
                skills,
                actors);

        assertThrows(
                RuntimeException.class,
                () -> controller.skillPackage(
                        ASSET_ID, RELEASE_ID, authentication));

        assertTrue(stream.closed);
    }

    private static SkillInstallManifest manifest(long length) {
        return manifest(length, "application/zip");
    }

    private static SkillInstallManifest manifest(
            long length, String mediaType) {
        return new SkillInstallManifest(
                ASSET_ID,
                RELEASE_ID,
                "support",
                "support-triage",
                "support/support-triage",
                "1.0.0",
                AssetPublicationMode.DIRECT,
                "Support triage",
                "Triage support issues",
                "b".repeat(64),
                "a".repeat(64),
                length,
                mediaType,
                "MIT",
                "Claude Code and Codex",
                "Read",
                Map.of(),
                List.of(new SkillInstallManifest.File(
                        "SKILL.md",
                        1,
                        "c".repeat(64))));
    }

    private static final class TrackingInputStream
            extends ByteArrayInputStream {

        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws java.io.IOException {
            closed = true;
            super.close();
        }
    }
}
