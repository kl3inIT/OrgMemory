package com.orgmemory.mcp;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * Binary companion transport for Skill installation.
 *
 * <p>The MCP JSON-RPC endpoint remains the discovery and manifest contract.
 * Packages stay binary and are proxied through the canonical API with an
 * exchanged actor token.
 */
@RestController
@RequestMapping("/skill-packages")
class SkillPackageController {

    private final AssetDeliveryApiClient assets;
    private final McpApiAuthorization authorization;

    SkillPackageController(
            AssetDeliveryApiClient assets,
            McpApiAuthorization authorization) {
        this.assets = assets;
        this.authorization = authorization;
    }

    @GetMapping("/{assetId}/releases/{releaseId}")
    ResponseEntity<StreamingResponseBody> packageContent(
            @PathVariable UUID assetId,
            @PathVariable UUID releaseId,
            Authentication authentication) {
        String apiAuthorization = authorization.require(authentication);
        var manifest = assets.getSkillManifest(
                apiAuthorization, assetId, releaseId);
        StreamingResponseBody body = output -> assets.copySkillPackage(
                apiAuthorization, assetId, releaseId, output);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(manifest.mediaType()))
                .contentLength(manifest.packageLength())
                .cacheControl(CacheControl.noStore())
                .eTag(manifest.packageDigest())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(
                                        fileName(manifest),
                                        StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    static String packagePath(UUID assetId, UUID releaseId) {
        return "/skill-packages/"
                + assetId
                + "/releases/"
                + releaseId;
    }

    private static String fileName(
            AssetDeliveryApiClient.SkillManifest manifest) {
        String version = manifest.version()
                .replaceAll("[^A-Za-z0-9._-]", "-");
        return manifest.slug() + "-" + version + ".zip";
    }
}
