package com.orgmemory.connectors.github;

import com.orgmemory.core.assetregistry.SkillGitHubSourcePort;
import com.orgmemory.core.shared.error.BusinessValidationException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

/** Converts one bounded GitHub tarball into independently validated Skill ZIP packages. */
final class GitHubSkillArchiveReader {

    private static final int MAX_ARCHIVE_ENTRIES = 10_000;
    private static final long MAX_EXPANDED_BYTES = 50L * 1024 * 1024;
    private static final int MAX_SKILLS = 20;
    private static final int MAX_SKILL_ZIP_BYTES = 20 * 1024 * 1024;

    private GitHubSkillArchiveReader() {
    }

    static List<SkillGitHubSourcePort.FetchedPackage> read(
            byte[] archive, String subpath) {
        Map<String, byte[]> files = extract(archive, subpath);
        List<String> manifests = files.keySet().stream()
                .filter(path -> path.equals("SKILL.md") || path.endsWith("/SKILL.md"))
                .sorted()
                .toList();
        if (manifests.isEmpty()) {
            throw new BusinessValidationException(
                    "skill.github-no-skills",
                    "No SKILL.md files were found at the selected repository path");
        }
        if (manifests.size() > MAX_SKILLS) {
            throw new BusinessValidationException(
                    "skill.github-too-many-skills",
                    "A repository import may contain at most 20 Skills");
        }
        List<Root> roots = manifests.stream()
                .map(manifest -> new Root(manifest, parent(manifest)))
                .sorted(Comparator.comparingInt((Root root) -> root.directory().length()).reversed())
                .toList();
        Map<String, Map<String, byte[]>> assigned = new LinkedHashMap<>();
        manifests.forEach(manifest -> assigned.put(manifest, new LinkedHashMap<>()));
        for (Map.Entry<String, byte[]> file : files.entrySet()) {
            Root owner = roots.stream()
                    .filter(root -> contains(root.directory(), file.getKey()))
                    .findFirst()
                    .orElse(null);
            if (owner == null) {
                continue;
            }
            String relative = owner.directory().isEmpty()
                    ? file.getKey()
                    : file.getKey().substring(owner.directory().length() + 1);
            assigned.get(owner.manifest()).put(relative, file.getValue());
        }
        List<SkillGitHubSourcePort.FetchedPackage> packages = new ArrayList<>();
        for (String manifest : manifests) {
            try {
                packages.add(new SkillGitHubSourcePort.FetchedPackage(
                        sourcePath(subpath, manifest),
                        zip(assigned.get(manifest)),
                        "",
                        ""));
            } catch (PackageTooLargeException tooLarge) {
                packages.add(new SkillGitHubSourcePort.FetchedPackage(
                        sourcePath(subpath, manifest),
                        null,
                        "skill.github-package-too-large",
                        "This Skill package exceeds the 20 MiB import limit"));
            }
        }
        return packages;
    }

    private static Map<String, byte[]> extract(byte[] archive, String subpath) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        long expanded = 0;
        int entries = 0;
        try (var gzip = new GzipCompressorInputStream(new ByteArrayInputStream(archive));
                var tar = new TarArchiveInputStream(gzip)) {
            TarArchiveEntry entry;
            while ((entry = tar.getNextEntry()) != null) {
                entries++;
                if (entries > MAX_ARCHIVE_ENTRIES) {
                    throw invalid("The GitHub archive contains too many entries");
                }
                if (entry.isSymbolicLink()
                        || entry.isLink()
                        || (!entry.isDirectory() && !entry.isFile())) {
                    throw invalid("The GitHub archive contains an unsupported entry");
                }
                String path = stripGitHubRoot(entry.getName());
                if (path.isEmpty()) {
                    continue;
                }
                requireSafePath(path);
                String collision = path.toLowerCase(Locale.ROOT);
                if (!names.add(collision)) {
                    throw invalid("The GitHub archive contains duplicate or case-colliding paths");
                }
                long size = entry.getSize();
                if (size < 0 || size > MAX_EXPANDED_BYTES - expanded) {
                    throw invalid("The expanded GitHub archive exceeds 50 MiB");
                }
                expanded += size;
                if (entry.isDirectory() || !inside(subpath, path)) {
                    continue;
                }
                String selectedPath = subpath.isEmpty()
                        ? path
                        : path.substring(subpath.length() + 1);
                byte[] content = tar.readNBytes(Math.toIntExact(size) + 1);
                if (content.length != size) {
                    throw invalid("The GitHub archive entry length is inconsistent");
                }
                files.put(selectedPath, content);
            }
        } catch (BusinessValidationException invalid) {
            throw invalid;
        } catch (IOException | ArithmeticException unreadable) {
            throw new BusinessValidationException(
                    "skill.github-archive-invalid",
                    "The GitHub repository archive could not be read",
                    unreadable);
        }
        return files;
    }

    private static byte[] zip(Map<String, byte[]> files) {
        BoundedOutputStream bounded = new BoundedOutputStream(MAX_SKILL_ZIP_BYTES);
        try (ZipOutputStream zip = new ZipOutputStream(bounded)) {
            for (Map.Entry<String, byte[]> file : files.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .toList()) {
                ZipEntry entry = new ZipEntry(file.getKey());
                entry.setTime(0);
                zip.putNextEntry(entry);
                zip.write(file.getValue());
                zip.closeEntry();
            }
        } catch (PackageTooLargeException tooLarge) {
            throw tooLarge;
        } catch (IOException impossible) {
            throw new IllegalStateException("In-memory Skill ZIP creation failed", impossible);
        }
        return bounded.toByteArray();
    }

    private static String stripGitHubRoot(String path) {
        String normalized = path == null ? "" : path.replace('\\', '/');
        int separator = normalized.indexOf('/');
        if (separator < 0) {
            return "";
        }
        return normalized.substring(separator + 1).replaceFirst("/+$", "");
    }

    private static void requireSafePath(String path) {
        if (path.startsWith("/")
                || path.contains("//")
                || path.indexOf('\0') >= 0
                || List.of(path.split("/")).stream()
                        .anyMatch(segment -> segment.isBlank()
                                || ".".equals(segment)
                                || "..".equals(segment))) {
            throw invalid("The GitHub archive contains an unsafe path");
        }
    }

    private static boolean inside(String subpath, String path) {
        return subpath.isEmpty() || path.equals(subpath) || path.startsWith(subpath + "/");
    }

    private static boolean contains(String root, String path) {
        return root.isEmpty() || path.equals(root) || path.startsWith(root + "/");
    }

    private static String parent(String manifest) {
        int separator = manifest.lastIndexOf('/');
        return separator < 0 ? "" : manifest.substring(0, separator);
    }

    private static String sourcePath(String subpath, String manifest) {
        return subpath.isEmpty() ? manifest : subpath + "/" + manifest;
    }

    private static BusinessValidationException invalid(String message) {
        return new BusinessValidationException("skill.github-archive-invalid", message);
    }

    private record Root(String manifest, String directory) {
    }

    private static final class BoundedOutputStream extends ByteArrayOutputStream {

        private final int maximum;

        private BoundedOutputStream(int maximum) {
            this.maximum = maximum;
        }

        @Override
        public synchronized void write(int value) {
            requireCapacity(1);
            super.write(value);
        }

        @Override
        public synchronized void write(byte[] value, int offset, int length) {
            requireCapacity(length);
            super.write(value, offset, length);
        }

        private void requireCapacity(int additional) {
            if (additional < 0 || count > maximum - additional) {
                throw new PackageTooLargeException();
            }
        }
    }

    private static final class PackageTooLargeException extends RuntimeException {
    }
}
