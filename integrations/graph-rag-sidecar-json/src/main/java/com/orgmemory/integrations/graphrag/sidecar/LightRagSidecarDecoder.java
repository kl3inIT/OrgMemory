package com.orgmemory.integrations.graphrag.sidecar;

import com.orgmemory.graphrag.multimodal.MultimodalPayload;
import com.orgmemory.graphrag.multimodal.MultimodalSidecar;
import com.orgmemory.graphrag.multimodal.MultimodalSidecarItem;
import com.orgmemory.graphrag.multimodal.MultimodalModality;
import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import com.orgmemory.graphrag.parsing.DocumentParseResult;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Strict decoder for the LightRAG 1.0 split sidecar format.
 *
 * <p>Stored {@code llm_analyze_result} values are intentionally ignored: OrgMemory recomputes
 * analysis under its immutable model and authorization profile.
 */
public final class LightRagSidecarDecoder {

    private final ObjectMapper objectMapper;
    private final LightRagArtifactResolver artifactResolver;

    public LightRagSidecarDecoder(
            ObjectMapper objectMapper,
            LightRagArtifactResolver artifactResolver) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.artifactResolver =
                Objects.requireNonNull(artifactResolver, "artifactResolver");
    }

    public DocumentParseResult decode(LightRagSidecarBundle bundle) {
        Objects.requireNonNull(bundle, "bundle");
        ParsedBlocks parsed = parseBlocks(bundle.blocksJsonl());
        var items = new ArrayList<MultimodalSidecarItem>();
        decodeItems(
                bundle.drawingsJson(),
                "drawings",
                MultimodalModality.IMAGE,
                parsed,
                items);
        decodeItems(
                bundle.tablesJson(),
                "tables",
                MultimodalModality.TABLE,
                parsed,
                items);
        decodeItems(
                bundle.equationsJson(),
                "equations",
                MultimodalModality.EQUATION,
                parsed,
                items);
        MultimodalSidecar sidecar = new MultimodalSidecar(
                MultimodalSidecar.SCHEMA_VERSION,
                text(parsed.meta, "doc_id"),
                text(parsed.meta, "document_name"),
                text(parsed.meta, "document_format"),
                text(parsed.meta, "parse_engine"),
                parsed.document.contentSha256(),
                items);
        return new DocumentParseResult(
                parsed.document,
                mediaType(text(parsed.meta, "document_format")),
                Map.of(
                        "sidecar.format", "lightrag",
                        "sidecar.version", text(parsed.meta, "version"),
                        "parser.component", text(parsed.meta, "parse_engine")),
                Optional.of(sidecar));
    }

    private ParsedBlocks parseBlocks(String jsonl) {
        String[] lines = jsonl.replace("\r\n", "\n")
                .replace('\r', '\n')
                .split("\n");
        if (lines.length < 2) {
            throw new IllegalArgumentException(
                    "blocks.jsonl must contain metadata and content");
        }
        JsonNode meta = read(lines[0]);
        if (!"meta".equals(text(meta, "type"))
                || !"lightrag".equals(text(meta, "format"))
                || !"1.0".equals(text(meta, "version"))) {
            throw new IllegalArgumentException("unsupported LightRAG sidecar metadata");
        }
        var contentRows = new ArrayList<JsonNode>();
        for (int index = 1; index < lines.length; index++) {
            if (!lines[index].isBlank()) {
                JsonNode row = read(lines[index]);
                if (!"content".equals(text(row, "type"))) {
                    throw new IllegalArgumentException(
                            "blocks.jsonl contains a non-content row");
                }
                contentRows.add(row);
            }
        }
        if (meta.path("blocks").asInt(-1) != contentRows.size()) {
            throw new IllegalArgumentException("blocks.jsonl count does not match metadata");
        }

        StringBuilder canonical = new StringBuilder();
        var blocks = new ArrayList<DocumentBlock>();
        var blocksById = new HashMap<String, BlockAnchor>();
        for (int index = 0; index < contentRows.size(); index++) {
            JsonNode row = contentRows.get(index);
            if (!canonical.isEmpty()) {
                canonical.append("\n\n");
            }
            int start = canonical.length();
            String content = text(row, "content").strip();
            canonical.append(content);
            int end = canonical.length();
            int level = row.path("level").asInt(0);
            String blockId = text(row, "blockid");
            blocks.add(new DocumentBlock(
                    index,
                    level > 0 ? DocumentBlockKind.HEADING : DocumentBlockKind.PARAGRAPH,
                    start,
                    end,
                    null,
                    null,
                    level > 0 ? Math.min(level, 6) : null,
                    Map.of(
                            "sidecar.blockId", blockId,
                            "sidecar.sessionType",
                            row.path("session_type").asString("body"))));
            if (blocksById.putIfAbsent(blockId, new BlockAnchor(index, start, content))
                    != null) {
                throw new IllegalArgumentException(
                        "duplicate blockid in blocks.jsonl: " + blockId);
            }
        }
        String canonicalText = canonical.toString();
        String hash = ResolvedDocumentProcessingProfile.sha256(canonicalText);
        if (!("sha256:" + hash).equals(text(meta, "document_hash"))) {
            throw new IllegalArgumentException(
                    "LightRAG document hash does not match canonical content");
        }
        return new ParsedBlocks(
                meta,
                new CanonicalDocument(canonicalText, hash, blocks),
                Map.copyOf(blocksById));
    }

    private void decodeItems(
            Optional<String> json,
            String containerName,
            MultimodalModality modality,
            ParsedBlocks parsed,
            List<MultimodalSidecarItem> items) {
        if (json.isEmpty()) {
            return;
        }
        JsonNode root = read(json.orElseThrow());
        if (!"1.0".equals(text(root, "version"))) {
            throw new IllegalArgumentException(
                    "unsupported LightRAG " + containerName + " version");
        }
        JsonNode container = root.path(containerName);
        if (!container.isObject()) {
            throw new IllegalArgumentException(containerName + " must be an object");
        }
        for (Map.Entry<String, JsonNode> entry : container.properties()) {
            JsonNode node = entry.getValue();
            String itemId = text(node, "id");
            if (!entry.getKey().equals(itemId)) {
                throw new IllegalArgumentException(
                        containerName + " key must match item id");
            }
            BlockAnchor block = parsed.blocksById.get(text(node, "blockid"));
            if (block == null) {
                throw new IllegalArgumentException(
                        "sidecar item references an unknown block");
            }
            TargetSpan target = locateTarget(block.content, itemId, modality);
            List<String> headings = strings(node.path("parent_headings"));
            String heading = node.path("heading").asString("").strip();
            if (!heading.isEmpty()) {
                headings = new ArrayList<>(headings);
                headings.add(heading);
            }
            items.add(new MultimodalSidecarItem(
                    itemId,
                    modality,
                    block.index,
                    block.globalStart + target.start,
                    block.globalStart + target.end,
                    headings,
                    node.path("caption").asString(""),
                    String.join("\n", strings(node.path("footnotes"))),
                    payload(node, modality, itemId),
                    sidecarAttributes(node)));
        }
    }

    private MultimodalPayload payload(
            JsonNode node,
            MultimodalModality modality,
            String itemId) {
        return switch (modality) {
            case IMAGE -> {
                String path = safeRelativeAssetPath(text(node, "path"));
                String imageMediaType = imageMediaType(text(node, "format"));
                yield new MultimodalPayload.Image(
                        artifactResolver.resolve(itemId, path, imageMediaType));
            }
            case TABLE -> {
                JsonNode dimensions = node.path("dimension");
                Integer rows = dimensions.isArray() && dimensions.size() == 2
                        ? dimensions.get(0).asInt()
                        : null;
                Integer columns = dimensions.isArray() && dimensions.size() == 2
                        ? dimensions.get(1).asInt()
                        : null;
                yield new MultimodalPayload.Table(
                        text(node, "format"),
                        text(node, "content"),
                        rows,
                        columns);
            }
            case EQUATION -> new MultimodalPayload.Equation(text(node, "content"));
        };
    }

    private static Map<String, String> sidecarAttributes(JsonNode node) {
        var attributes = new HashMap<String, String>();
        String selfRef = node.path("self_ref").asString("").strip();
        if (!selfRef.isEmpty()) {
            attributes.put("sidecar.selfRef", selfRef);
        }
        String format = node.path("format").asString("").strip();
        if (!format.isEmpty()) {
            attributes.put("sidecar.format", format);
        }
        return Map.copyOf(attributes);
    }

    private JsonNode read(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("invalid LightRAG sidecar JSON", exception);
        }
    }

    private static TargetSpan locateTarget(
            String content,
            String itemId,
            MultimodalModality modality) {
        int id = content.indexOf(itemId);
        if (id < 0) {
            throw new IllegalArgumentException(
                    "sidecar item is not anchored in its content block");
        }
        int start = content.lastIndexOf('<', id);
        String closing = switch (modality) {
            case IMAGE -> "/>";
            case TABLE -> "</table>";
            case EQUATION -> "</equation>";
        };
        int closingStart = content.indexOf(closing, id);
        if (start < 0 || closingStart < 0) {
            throw new IllegalArgumentException("sidecar target placeholder is malformed");
        }
        return new TargetSpan(start, closingStart + closing.length());
    }

    private static List<String> strings(JsonNode array) {
        if (!array.isArray()) {
            return List.of();
        }
        var values = new ArrayList<String>();
        array.forEach(value -> {
            String text = value.asString("").strip();
            if (!text.isEmpty()) {
                values.add(text);
            }
        });
        return List.copyOf(values);
    }

    private static String safeRelativeAssetPath(String value) {
        String path = textValue(value, "path");
        if (path.contains("\\")
                || path.startsWith("/")
                || path.matches("^[A-Za-z]:.*")
                || path.contains("://")) {
            throw new IllegalArgumentException("image asset path must be bundle-relative");
        }
        for (String segment : path.split("/")) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException(
                        "image asset path contains an unsafe segment");
            }
        }
        return path;
    }

    private static String imageMediaType(String format) {
        return switch (format.toLowerCase()) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/" + format.toLowerCase();
        };
    }

    private static String mediaType(String format) {
        return switch (format.toLowerCase()) {
            case "pdf" -> "application/pdf";
            case "docx" ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "pptx" ->
                "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "xlsx" ->
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "md", "markdown" -> "text/markdown";
            case "txt" -> "text/plain";
            default -> "application/octet-stream";
        };
    }

    private static String text(JsonNode node, String field) {
        return textValue(node.path(field).asString(""), field);
    }

    private static String textValue(String value, String field) {
        String normalized = Objects.requireNonNull(value, field).strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }

    private record ParsedBlocks(
            JsonNode meta,
            CanonicalDocument document,
            Map<String, BlockAnchor> blocksById) {}

    private record BlockAnchor(int index, int globalStart, String content) {}

    private record TargetSpan(int start, int end) {}
}
