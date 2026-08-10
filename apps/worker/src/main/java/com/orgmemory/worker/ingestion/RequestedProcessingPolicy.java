package com.orgmemory.worker.ingestion;

import com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot;
import com.orgmemory.graphrag.chunking.ChunkerRegistrySnapshot;
import com.orgmemory.graphrag.chunking.FixedTokenChunker;
import com.orgmemory.graphrag.chunking.ParagraphSemanticChunker;
import com.orgmemory.graphrag.chunking.RecursiveCharacterChunker;
import com.orgmemory.graphrag.chunking.RecursiveCharacterOptions;
import com.orgmemory.graphrag.chunking.SemanticVectorChunker;
import com.orgmemory.graphrag.chunking.SemanticVectorOptions;
import com.orgmemory.graphrag.parsing.ParserRegistrySnapshot;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.integrations.graphrag.springai.JtokkitTextTokenizer;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Requested parser/policy/options snapshot pinned before an ingestion attempt starts. */
record RequestedProcessingPolicy(Map<String, String> values) {

    static final String STRUCTURED_BLOCK_V1 = "structured-block-v1";
    static final String FIXED_TOKEN_V1 = "fixed-token-v1";
    static final String RECURSIVE_CHARACTER_V1 = "recursive-character-v1";
    static final String SEMANTIC_VECTOR_V1 = "semantic-vector-v1";
    private static final String SCHEMA_VERSION = "1";
    private static final String DISPATCH_VERSION = "canonical-block-kind-v1";
    private static final String UNIT_SEPARATOR = "\u001f";

    RequestedProcessingPolicy {
        values = Map.copyOf(new TreeMap<>(Objects.requireNonNull(values, "values")));
        require(values, "schemaVersion");
        if (!SCHEMA_VERSION.equals(values.get("schemaVersion"))) {
            throw new IllegalArgumentException(
                    "unsupported requested processing policy schema " + values.get("schemaVersion"));
        }
        requestedChunkerId(values.get("policy.id"));
    }

    static RequestedProcessingPolicy capture(
            SourceProcessingProperties properties,
            ParserRegistrySnapshot parsers,
            ChunkerRegistrySnapshot chunkers) {
        String policyId = properties.policyId();
        String chunkerId = requestedChunkerId(policyId);
        ProcessingComponentRef parser = parsers.require(properties.parserId()).component();
        ProcessingComponentRef chunker = chunkers.require(chunkerId).component();
        ProcessingComponentRef recursive =
                chunkers.require(RecursiveCharacterChunker.COMPONENT.id()).component();
        ProcessingComponentRef tokenizer =
                new JtokkitTextTokenizer(properties.tokenizerEncoding()).component();

        var resolved = new TreeMap<String, String>();
        resolved.put("schemaVersion", SCHEMA_VERSION);
        resolved.put("pipeline.version", properties.pipelineVersion());
        resolved.put("parser.id", parser.id());
        resolved.put("parser.version", parser.version());
        resolved.put("policy.id", policyId);
        resolved.put(
                "dispatch.version",
                STRUCTURED_BLOCK_V1.equals(policyId)
                        ? DISPATCH_VERSION
                        : "whole-document-v1");
        resolved.put("chunker.requested.id", chunker.id());
        resolved.put("chunker.requested.version", chunker.version());
        resolved.put("tokenizer.id", tokenizer.id());
        resolved.put("tokenizer.version", tokenizer.version());
        resolved.put("tokenizer.encoding", properties.tokenizerEncoding());
        resolved.put("normalizer.version", properties.normalizerVersion());
        resolved.put("embedding.provider", properties.embeddingProvider());
        resolved.put("embedding.model", properties.embeddingModel());
        resolved.put("embedding.dimensions", Integer.toString(properties.embeddingDimensions()));
        resolved.put(
                "embedding.semanticBatchSize",
                Integer.toString(properties.semanticEmbeddingBatchSize()));
        resolved.put("chunk.size", Integer.toString(properties.chunkSize()));
        resolved.put("chunk.overlap", Integer.toString(properties.chunkOverlap()));
        resolved.put("chunk.maximum.default", Integer.toString(properties.maximumChunks()));
        properties.maximumChunksByFormat().forEach((suffix, maximum) ->
                resolved.put("chunk.maximum." + suffix, Integer.toString(maximum)));

        if (STRUCTURED_BLOCK_V1.equals(policyId)) {
            resolved.put("dispatch.heading", "paragraph-semantic");
            resolved.put("dispatch.paragraph", "paragraph-semantic");
            resolved.put("dispatch.table", "paragraph-semantic:table-row-v1");
            resolved.put("dispatch.unstructured", "recursive-character");
            resolved.put("paragraph.shortAnchorChars", "100");
            resolved.put("paragraph.dropReferences", "false");
            resolved.put(
                    "paragraph.referencePrefixes",
                    String.join(
                            UNIT_SEPARATOR,
                            List.of(
                                    "references",
                                    "bibliography",
                                    "参考文献",
                                    "tài liệu tham khảo")));
            addRecursiveOptions(resolved, recursive, properties);
        } else if (RECURSIVE_CHARACTER_V1.equals(policyId)) {
            addRecursiveOptions(resolved, recursive, properties);
        } else if (SEMANTIC_VECTOR_V1.equals(policyId)) {
            resolved.put("semantic.bufferSize", "1");
            resolved.put(
                    "semantic.threshold",
                    SemanticVectorOptions.BreakpointThreshold.PERCENTILE.name());
            resolved.put("semantic.thresholdAmount", "95.0");
            resolved.put(
                    "semantic.sentenceSplitRegex",
                    SemanticVectorOptions.DEFAULT_SENTENCE_SPLIT_REGEX);
            addRecursiveOptions(resolved, recursive, properties);
        } else if (FIXED_TOKEN_V1.equals(policyId)) {
            resolved.put("fixed.splitOnly", "false");
        }
        return new RequestedProcessingPolicy(resolved);
    }

    static RequestedProcessingPolicy restore(DocumentProcessingProfileSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        var parsed = new TreeMap<String, String>();
        for (String line : snapshot.canonicalForm().split("\\n")) {
            if (line.isEmpty()) {
                continue;
            }
            int separator = line.indexOf('=');
            if (separator <= 0) {
                throw new IllegalArgumentException("malformed requested processing policy");
            }
            String previous = parsed.put(
                    line.substring(0, separator),
                    decode(line.substring(separator + 1)));
            if (previous != null) {
                throw new IllegalArgumentException("duplicate requested processing policy key");
            }
        }
        RequestedProcessingPolicy policy = new RequestedProcessingPolicy(parsed);
        if (!policy.snapshot().equals(snapshot)) {
            throw new IllegalArgumentException("requested processing policy is not canonical");
        }
        return policy;
    }

    DocumentProcessingProfileSnapshot snapshot() {
        StringBuilder canonical = new StringBuilder();
        values.forEach((key, value) -> canonical
                .append(key)
                .append('=')
                .append(encode(value))
                .append('\n'));
        return DocumentProcessingProfileSnapshot.from(canonical.toString());
    }

    void validateAvailable(
            ParserRegistrySnapshot parsers,
            ChunkerRegistrySnapshot chunkers) {
        requireComponent(
                parsers.require(parserId()).component(),
                value("parser.id"),
                value("parser.version"));
        requireComponent(
                chunkers.require(requestedChunkerId()).component(),
                value("chunker.requested.id"),
                value("chunker.requested.version"));
        if (values.containsKey("chunker.recursive.id")) {
            requireComponent(
                    chunkers.require(value("chunker.recursive.id")).component(),
                    value("chunker.recursive.id"),
                    value("chunker.recursive.version"));
        }
        ProcessingComponentRef tokenizer = new JtokkitTextTokenizer(tokenizerEncoding()).component();
        requireComponent(tokenizer, value("tokenizer.id"), value("tokenizer.version"));
    }

    String policyId() {
        return value("policy.id");
    }

    String parserId() {
        return value("parser.id");
    }

    String parserVersion() {
        return value("parser.version");
    }

    String requestedChunkerId() {
        return value("chunker.requested.id");
    }

    String tokenizerEncoding() {
        return value("tokenizer.encoding");
    }

    String pipelineVersion() {
        return value("pipeline.version");
    }

    String normalizerVersion() {
        return value("normalizer.version");
    }

    String embeddingProvider() {
        return value("embedding.provider");
    }

    String embeddingModel() {
        return value("embedding.model");
    }

    int embeddingDimensions() {
        return integer("embedding.dimensions");
    }

    int semanticEmbeddingBatchSize() {
        return integer("embedding.semanticBatchSize");
    }

    int chunkSize() {
        return integer("chunk.size");
    }

    int chunkOverlap() {
        return integer("chunk.overlap");
    }

    int maximumChunks(String suffix) {
        String key = "chunk.maximum." + suffix;
        return values.containsKey(key) ? integer(key) : integer("chunk.maximum.default");
    }

    int shortAnchorChars() {
        return integer("paragraph.shortAnchorChars");
    }

    boolean dropReferences() {
        return Boolean.parseBoolean(value("paragraph.dropReferences"));
    }

    List<String> referencePrefixes() {
        return List.of(value("paragraph.referencePrefixes").split(UNIT_SEPARATOR, -1));
    }

    List<String> recursiveSeparators() {
        return List.of(value("recursive.separators").split(UNIT_SEPARATOR, -1));
    }

    boolean recursiveKeepSeparator() {
        return Boolean.parseBoolean(value("recursive.keepSeparator"));
    }

    Map<String, String> resolvedProfileOptions() {
        var result = new TreeMap<String, String>();
        values.forEach((key, value) -> result.put("request." + key, value));
        result.put("request.sha256", snapshot().sha256());
        return Map.copyOf(result);
    }

    private String value(String key) {
        return require(values, key);
    }

    private int integer(String key) {
        return Integer.parseInt(value(key));
    }

    private static void addRecursiveOptions(
            Map<String, String> target,
            ProcessingComponentRef recursive,
            SourceProcessingProperties properties) {
        target.put("chunker.recursive.id", recursive.id());
        target.put("chunker.recursive.version", recursive.version());
        target.put("recursive.keepSeparator", "true");
        target.put(
                "recursive.separators",
                String.join(UNIT_SEPARATOR, RecursiveCharacterOptions.DEFAULT_SEPARATORS));
        target.put("recursive.chunkSize", Integer.toString(properties.chunkSize()));
        target.put("recursive.overlap", Integer.toString(properties.chunkOverlap()));
    }

    private static String requestedChunkerId(String policyId) {
        return switch (Objects.requireNonNull(policyId, "policyId")) {
            case STRUCTURED_BLOCK_V1 -> ParagraphSemanticChunker.COMPONENT.id();
            case FIXED_TOKEN_V1 -> FixedTokenChunker.COMPONENT.id();
            case RECURSIVE_CHARACTER_V1 -> RecursiveCharacterChunker.COMPONENT.id();
            case SEMANTIC_VECTOR_V1 -> SemanticVectorChunker.COMPONENT.id();
            default -> throw new IllegalArgumentException("unknown ingestion policy " + policyId);
        };
    }

    private static void requireComponent(
            ProcessingComponentRef actual,
            String expectedId,
            String expectedVersion) {
        if (!actual.id().equals(expectedId) || !actual.version().equals(expectedVersion)) {
            throw new IllegalStateException(
                    "pinned processing component is unavailable: "
                            + expectedId
                            + "@"
                            + expectedVersion);
        }
    }

    private static String require(Map<String, String> source, String key) {
        String value = source.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("requested processing policy is missing " + key);
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
