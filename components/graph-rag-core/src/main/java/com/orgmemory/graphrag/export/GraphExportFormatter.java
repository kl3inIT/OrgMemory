package com.orgmemory.graphrag.export;

import com.orgmemory.graphrag.model.EvidenceReference;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;

/** Deterministic, dependency-free formatter for authorized graph exports. */
public final class GraphExportFormatter {

    public Artifact format(GraphExportDocument document, GraphExportFormat format) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(format, "format");
        GraphExportDocument canonical = canonical(document);
        String content = switch (format) {
            case JSON -> json(canonical);
            case CSV -> csv(canonical);
            case MARKDOWN -> markdown(canonical);
            case TEXT -> text(canonical);
        };
        return new Artifact(format.mediaType(), format.extension(), content);
    }

    private static GraphExportDocument canonical(GraphExportDocument document) {
        return new GraphExportDocument(
                document.entities().stream()
                        .sorted(Comparator.comparing(GraphExportDocument.EntityRow::id))
                        .toList(),
                document.relations().stream()
                        .sorted(Comparator.comparing(GraphExportDocument.RelationRow::id))
                        .toList());
    }

    private static String json(GraphExportDocument document) {
        String entities = document.entities().stream()
                .map(entity -> "{\"id\":\"" + entity.id()
                        + "\",\"name\":\"" + jsonEscape(entity.name())
                        + "\",\"type\":\"" + jsonEscape(entity.type())
                        + "\",\"description\":\""
                        + jsonEscape(entity.description())
                        + "\",\"evidence\":" + evidenceJson(entity.evidence()) + "}")
                .collect(java.util.stream.Collectors.joining(","));
        String relations = document.relations().stream()
                .map(relation -> "{\"id\":\"" + relation.id()
                        + "\",\"sourceEntityId\":\"" + relation.sourceEntityId()
                        + "\",\"targetEntityId\":\"" + relation.targetEntityId()
                        + "\",\"type\":\"" + jsonEscape(relation.type())
                        + "\",\"keywords\":["
                        + relation.keywords().stream()
                                .map(value -> "\"" + jsonEscape(value) + "\"")
                                .collect(java.util.stream.Collectors.joining(","))
                        + "],\"description\":\"" + jsonEscape(relation.description())
                        + "\",\"weight\":" + relation.weight()
                        + ",\"evidence\":" + evidenceJson(relation.evidence()) + "}")
                .collect(java.util.stream.Collectors.joining(","));
        return "{\"entities\":[" + entities + "],\"relations\":[" + relations + "]}";
    }

    private static String csv(GraphExportDocument document) {
        StringBuilder output = new StringBuilder(
                "kind,id,name_or_source,target,type,description,weight,evidence\n");
        for (GraphExportDocument.EntityRow entity : document.entities()) {
            output.append(csv("entity")).append(',')
                    .append(csv(entity.id().toString())).append(',')
                    .append(csv(entity.name())).append(",,")
                    .append(csv(entity.type())).append(',')
                    .append(csv(entity.description())).append(",,")
                    .append(csv(evidenceText(entity.evidence()))).append('\n');
        }
        for (GraphExportDocument.RelationRow relation : document.relations()) {
            output.append(csv("relation")).append(',')
                    .append(csv(relation.id().toString())).append(',')
                    .append(csv(relation.sourceEntityId().toString())).append(',')
                    .append(csv(relation.targetEntityId().toString())).append(',')
                    .append(csv(relation.type())).append(',')
                    .append(csv(relation.description())).append(',')
                    .append(relation.weight()).append(',')
                    .append(csv(evidenceText(relation.evidence()))).append('\n');
        }
        return output.toString();
    }

    private static String markdown(GraphExportDocument document) {
        StringBuilder output = new StringBuilder("# Graph export\n\n## Entities\n\n");
        for (GraphExportDocument.EntityRow entity : document.entities()) {
            output.append("- **").append(markdownEscape(entity.name())).append("** (`")
                    .append(entity.id()).append("`) — ")
                    .append(markdownEscape(entity.description())).append('\n');
        }
        output.append("\n## Relations\n\n");
        for (GraphExportDocument.RelationRow relation : document.relations()) {
            output.append("- `").append(relation.sourceEntityId()).append("` → `")
                    .append(relation.targetEntityId()).append("` — ")
                    .append(markdownEscape(relation.description())).append('\n');
        }
        return output.toString();
    }

    private static String text(GraphExportDocument document) {
        StringBuilder output = new StringBuilder("ENTITIES\n");
        for (GraphExportDocument.EntityRow entity : document.entities()) {
            output.append(entity.id()).append('\t').append(entity.name()).append('\t')
                    .append(entity.description()).append('\n');
        }
        output.append("\nRELATIONS\n");
        for (GraphExportDocument.RelationRow relation : document.relations()) {
            output.append(relation.id()).append('\t')
                    .append(relation.sourceEntityId()).append(" -> ")
                    .append(relation.targetEntityId()).append('\t')
                    .append(relation.description()).append('\n');
        }
        return output.toString();
    }

    private static String evidenceJson(List<EvidenceReference> evidence) {
        return evidence.stream()
                .sorted(Comparator.comparing(EvidenceReference::sourceRevisionId)
                        .thenComparing(item -> Objects.toString(item.chunkId(), "")))
                .map(item -> "{\"knowledgeAssetId\":\"" + item.knowledgeAssetId()
                        + "\",\"sourceRevisionId\":\"" + item.sourceRevisionId()
                        + "\",\"chunkId\":"
                        + (item.chunkId() == null
                                ? "null"
                                : "\"" + item.chunkId() + "\"")
                        + ",\"aclSnapshotId\":\"" + item.aclSnapshotId()
                        + "\",\"aclGeneration\":" + item.aclGeneration() + "}")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private static String evidenceText(List<EvidenceReference> evidence) {
        StringJoiner joiner = new StringJoiner("|");
        evidence.stream()
                .sorted(Comparator.comparing(EvidenceReference::sourceRevisionId)
                        .thenComparing(item -> Objects.toString(item.chunkId(), "")))
                .forEach(item -> joiner.add(item.knowledgeAssetId()
                        + "/" + item.sourceRevisionId()
                        + "/" + Objects.toString(item.chunkId(), "-")
                        + "@" + item.aclGeneration()));
        return joiner.toString();
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }

    private static String jsonEscape(String value) {
        StringBuilder escaped = new StringBuilder();
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            switch (current) {
                case '"' -> escaped.append("\\\"");
                case '\\' -> escaped.append("\\\\");
                case '\b' -> escaped.append("\\b");
                case '\f' -> escaped.append("\\f");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (current < 0x20) {
                        escaped.append("\\u%04x".formatted((int) current));
                    } else {
                        escaped.append(current);
                    }
                }
            }
        }
        return escaped.toString();
    }

    private static String markdownEscape(String value) {
        return value.replace("\\", "\\\\")
                .replace("*", "\\*")
                .replace("_", "\\_")
                .replace("`", "\\`");
    }

    public record Artifact(String mediaType, String extension, String content) {

        public Artifact {
            Objects.requireNonNull(mediaType, "mediaType");
            Objects.requireNonNull(extension, "extension");
            Objects.requireNonNull(content, "content");
        }
    }
}
