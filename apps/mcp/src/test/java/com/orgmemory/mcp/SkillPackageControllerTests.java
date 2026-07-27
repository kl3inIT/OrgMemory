package com.orgmemory.mcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

class SkillPackageControllerTests {

    private static final UUID ASSET_ID =
            UUID.fromString("85000000-0000-0000-0000-000000000003");
    private static final UUID RELEASE_ID =
            UUID.fromString("85000000-0000-0000-0000-000000000004");

    @Test
    void proxiesBinaryBytesWithAnExchangedApiToken() throws Exception {
        AssetDeliveryApiClient assets =
                mock(AssetDeliveryApiClient.class);
        McpApiAuthorization authorization =
                mock(McpApiAuthorization.class);
        Authentication authentication = mock(Authentication.class);
        String bearer = "Bearer exchanged";
        byte[] bytes = new byte[] {4, 5, 6};
        when(authorization.require(authentication)).thenReturn(bearer);
        when(assets.getSkillManifest(bearer, ASSET_ID, RELEASE_ID))
                .thenReturn(manifest(bytes.length));
        doAnswer(invocation -> {
                    java.io.OutputStream output = invocation.getArgument(3);
                    output.write(bytes);
                    return null;
                })
                .when(assets)
                .copySkillPackage(
                        org.mockito.ArgumentMatchers.eq(bearer),
                        org.mockito.ArgumentMatchers.eq(ASSET_ID),
                        org.mockito.ArgumentMatchers.eq(RELEASE_ID),
                        org.mockito.ArgumentMatchers.any());

        var response = new SkillPackageController(assets, authorization)
                .packageContent(
                        ASSET_ID, RELEASE_ID, authentication);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertArrayEquals(bytes, output.toByteArray());
        assertEquals(bytes.length, response.getHeaders().getContentLength());
        assertEquals(
                "\"" + "a".repeat(64) + "\"",
                response.getHeaders().getETag());
    }

    private static AssetDeliveryApiClient.SkillManifest manifest(
            long length) {
        return new AssetDeliveryApiClient.SkillManifest(
                ASSET_ID,
                RELEASE_ID,
                "support",
                "support-triage",
                "support/support-triage",
                "1.0.0",
                "Support triage",
                "Triage support issues",
                "b".repeat(64),
                "a".repeat(64),
                length,
                "application/zip",
                "MIT",
                "Claude Code and Codex",
                "Read",
                Map.of(),
                List.of(new AssetDeliveryApiClient.SkillFile(
                        "SKILL.md",
                        1,
                        "c".repeat(64))));
    }
}
