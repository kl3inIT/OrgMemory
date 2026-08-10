package com.orgmemory.core.assetregistry.skill;

import com.orgmemory.core.assetregistry.api.AssetUnavailableException;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseDeliveryQuery;
import com.orgmemory.core.assetregistry.skilldelivery.SkillReleaseSummary;
import com.orgmemory.core.organization.CurrentActor;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.springframework.stereotype.Service;

/**
 * Actor-scoped Skill activation without filesystem, shell, or package-code
 * execution.
 */
@Service
class SkillRuntimeService implements SkillRuntimeOperations {

    static final int MAX_RUNTIME_TEXT_BYTES = 128 * 1024;

    private final SkillReleaseDeliveryQuery deliveries;
    private final SkillDistributionOperations distribution;
    private final SkillActivationOperations activations;

    SkillRuntimeService(
            SkillReleaseDeliveryQuery deliveries,
            SkillDistributionOperations distribution,
            SkillActivationOperations activations) {
        this.deliveries = deliveries;
        this.distribution = distribution;
        this.activations = activations;
    }

    @Override
    public List<SkillSummary> search(
            CurrentActor actor, String query, int limit) {
        return deliveries.search(actor, query, limit).stream()
                .filter(release -> activations.isEnabled(actor, release.assetId()))
                .map(SkillRuntimeService::summary)
                .toList();
    }

    @Override
    public ActivatedSkill activate(
            CurrentActor actor, UUID assetId, UUID releaseId) {
        requireEnabled(actor, assetId);
        SkillPackageContent content = distribution.open(actor, assetId, releaseId);
        SkillInstallManifest manifest = content.manifest();
        String instructions = readEntry(content, "SKILL.md");
        List<String> resources = manifest.files().stream()
                .map(SkillInstallManifest.File::path)
                .filter(path -> !path.equals("SKILL.md"))
                .toList();
        return new ActivatedSkill(summary(manifest), instructions, resources);
    }

    @Override
    public SkillResource readResource(
            CurrentActor actor,
            UUID assetId,
            UUID releaseId,
            String path) {
        String safePath = requirePath(path);
        requireEnabled(actor, assetId);
        SkillPackageContent content = distribution.open(actor, assetId, releaseId);
        return new SkillResource(
                summary(content.manifest()),
                safePath,
                readEntry(content, safePath));
    }

    private static String readEntry(
            SkillPackageContent content, String path) {
        try (content;
                ZipArchiveInputStream zip = new ZipArchiveInputStream(
                        content.stream(), StandardCharsets.UTF_8.name(), true, true)) {
            SkillInstallManifest.File expected = content.manifest().files().stream()
                    .filter(file -> file.path().equals(path))
                    .findFirst()
                    .orElseThrow(() -> new AssetUnavailableException(
                            "The Skill resource is unavailable"));
            if (expected.size() > MAX_RUNTIME_TEXT_BYTES) {
                throw new AssetUnavailableException(
                        "The Skill resource exceeds the runtime text limit");
            }
            ZipArchiveEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String relative = relativePath(
                        entry.getName(), content.manifest());
                if (relative.equals(path)) {
                    byte[] bytes = readBounded(zip, MAX_RUNTIME_TEXT_BYTES);
                    verify(expected, bytes);
                    return decodeUtf8(bytes);
                }
            }
            throw new AssetUnavailableException("The Skill resource is unavailable");
        } catch (AssetUnavailableException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw new AssetUnavailableException(
                    "The Skill resource is unavailable", failure);
        }
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            total += read;
            if (total > maximum) {
                throw new AssetUnavailableException(
                        "The Skill resource exceeds the runtime text limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static void verify(
            SkillInstallManifest.File expected, byte[] bytes) {
        if (bytes.length != expected.size()
                || !digest(bytes).equals(expected.sha256())) {
            throw new AssetUnavailableException(
                    "The Skill resource failed its integrity check");
        }
    }

    private static String relativePath(
            String archivePath, SkillInstallManifest manifest) {
        String normalized = archivePath.endsWith("/")
                ? archivePath.substring(0, archivePath.length() - 1)
                : archivePath;
        if (declared(manifest, normalized)) {
            return normalized;
        }
        int separator = normalized.indexOf('/');
        String relative = separator < 0
                ? normalized
                : normalized.substring(separator + 1);
        if (!SkillPackageInspector.isSafeRelativePath(relative)
                || !declared(manifest, relative)) {
            throw new AssetUnavailableException("The Skill resource is unavailable");
        }
        return relative;
    }

    private static boolean declared(
            SkillInstallManifest manifest, String path) {
        return manifest.files().stream()
                .anyMatch(file -> file.path().equals(path));
    }

    private static String requirePath(String value) {
        String normalized = Objects.requireNonNull(value, "path").strip();
        if (!SkillPackageInspector.isSafeRelativePath(normalized)) {
            throw new IllegalArgumentException("Skill resource path is invalid");
        }
        return normalized;
    }

    private static String decodeUtf8(byte[] bytes) {
        try {
            String value = StandardCharsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (value.indexOf('\0') >= 0) {
                throw new AssetUnavailableException(
                        "The Skill resource is not runtime-readable text");
            }
            return value;
        } catch (CharacterCodingException failure) {
            throw new AssetUnavailableException(
                    "The Skill resource is not runtime-readable text", failure);
        }
    }

    private static String digest(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private void requireEnabled(CurrentActor actor, UUID assetId) {
        if (!activations.isEnabled(actor, assetId)) {
            throw new AssetUnavailableException("The Skill is not enabled for this user");
        }
    }

    private static SkillSummary summary(SkillReleaseSummary release) {
        return new SkillSummary(
                release.assetId(),
                release.releaseId(),
                release.namespace() + "/" + release.slug(),
                release.version(),
                release.title(),
                release.description(),
                release.releaseDigest());
    }

    private static SkillSummary summary(SkillInstallManifest manifest) {
        return new SkillSummary(
                manifest.assetId(),
                manifest.releaseId(),
                manifest.coordinate(),
                manifest.version(),
                manifest.title(),
                manifest.description(),
                manifest.releaseDigest());
    }
}
