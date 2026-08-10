package com.orgmemory.worker.ingestion;

import com.orgmemory.core.knowledge.sourceledger.DocumentProcessingProfileSnapshot;
import com.orgmemory.core.knowledge.sourceledger.ProcessingProfileMismatchException;
import com.orgmemory.graphrag.chunking.ChunkedText;
import com.orgmemory.graphrag.chunking.ChunkerOptions;
import com.orgmemory.graphrag.chunking.ChunkerRegistry;
import com.orgmemory.graphrag.chunking.ChunkerRegistrySnapshot;
import com.orgmemory.graphrag.chunking.ChunkingRequest;
import com.orgmemory.graphrag.chunking.FixedTokenChunker;
import com.orgmemory.graphrag.chunking.FixedTokenOptions;
import com.orgmemory.graphrag.chunking.ParagraphSemanticChunker;
import com.orgmemory.graphrag.chunking.ParagraphSemanticOptions;
import com.orgmemory.graphrag.chunking.RecursiveCharacterChunker;
import com.orgmemory.graphrag.chunking.RecursiveCharacterOptions;
import com.orgmemory.graphrag.chunking.SemanticEmbeddingInvocationException;
import com.orgmemory.graphrag.chunking.SemanticEmbeddingUnavailableException;
import com.orgmemory.graphrag.chunking.SemanticVectorChunker;
import com.orgmemory.graphrag.chunking.SemanticVectorOptions;
import com.orgmemory.graphrag.parsing.DocumentParseRequest;
import com.orgmemory.graphrag.parsing.ParserRegistry;
import com.orgmemory.graphrag.parsing.ParserRegistrySnapshot;
import com.orgmemory.graphrag.parsing.ParserSpec;
import com.orgmemory.graphrag.parsing.PassthroughParser;
import com.orgmemory.graphrag.parsing.ReuseParser;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import com.orgmemory.graphrag.processing.ResolvedDocumentProcessingProfile;
import com.orgmemory.integrations.graphrag.springai.JtokkitTextTokenizer;
import com.orgmemory.integrations.graphrag.springai.SpringAiTextEmbeddingPort;
import com.orgmemory.integrations.documentparsing.springai.SpringAiDocumentParser;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/** Resolves one immutable parser/chunker snapshot and executes the full PR3 engine. */
@Component
final class DocumentProcessingEngine {

    private final SourceProcessingProperties properties;
    private final ParserRegistrySnapshot parsers;
    private final ChunkerRegistrySnapshot chunkers;

    DocumentProcessingEngine(
            SourceProcessingProperties properties,
            SpringAiDocumentParser documentParser) {
        this.properties = properties;
        ClassLoader classLoader = DocumentProcessingEngine.class.getClassLoader();
        this.parsers = new ParserRegistry()
                .register(new ParserSpec(
                        documentParser.component(),
                        documentParser.supportedSuffixes(),
                        true,
                        true,
                        "",
                        documentParser))
                .register(new ParserSpec(
                        PassthroughParser.COMPONENT,
                        java.util.Set.of("txt", "md"),
                        false,
                        true,
                        "",
                        new PassthroughParser()))
                .register(new ParserSpec(
                        ReuseParser.COMPONENT,
                        java.util.Set.of("canonical"),
                        false,
                        true,
                        "",
                        new ReuseParser()))
                .loadPlugins(classLoader)
                .snapshot();
        this.chunkers = new ChunkerRegistry()
                .register(new FixedTokenChunker())
                .register(new RecursiveCharacterChunker())
                .register(new SemanticVectorChunker())
                .register(new ParagraphSemanticChunker())
                .loadPlugins(classLoader)
                .snapshot();
    }

    DocumentProcessingProfileSnapshot requestedProcessingProfile() {
        return RequestedProcessingPolicy.capture(properties, parsers, chunkers).snapshot();
    }

    RequestedProcessingPolicy requestedPolicy(DocumentProcessingProfileSnapshot snapshot) {
        RequestedProcessingPolicy policy = RequestedProcessingPolicy.restore(snapshot);
        policy.validateAvailable(parsers, chunkers);
        return policy;
    }

    ProcessedSourceDocument process(
            DocumentParseRequest request,
            EmbeddingModel embeddingModel) {
        return process(
                request,
                embeddingModel,
                requestedProcessingProfile(),
                Optional.empty());
    }

    ProcessedSourceDocument process(
            DocumentParseRequest request,
            EmbeddingModel embeddingModel,
            DocumentProcessingProfileSnapshot requestedSnapshot,
            Optional<DocumentProcessingProfileSnapshot> resolvedSnapshot) {
        RequestedProcessingPolicy policy = requestedPolicy(requestedSnapshot);
        JtokkitTextTokenizer tokenizer = new JtokkitTextTokenizer(policy.tokenizerEncoding());
        ParserSpec parser = parsers.route(request.suffix(), policy.parserId());
        if (!parser.component().version().equals(policy.parserVersion())) {
            throw new IllegalStateException("pinned parser version is unavailable");
        }
        long parseStartedAt = System.nanoTime();
        var parsed = parser.parser().parse(request);
        int maximumChunks = policy.maximumChunks(request.suffix());
        Duration parseDuration =
                Duration.ofNanos(Math.max(0, System.nanoTime() - parseStartedAt));
        long minimumChunks = Math.ceilDiv(
                (long) tokenizer.count(parsed.document().content()),
                policy.chunkSize());
        if (minimumChunks > maximumChunks) {
            throw new RejectedSourceException(
                    "CHUNK_LIMIT_EXCEEDED",
                    "The ." + request.suffix() + " document exceeds its "
                            + maximumChunks + " chunk limit");
        }
        var semanticEmbedding = new SpringAiTextEmbeddingPort(
                embeddingModel,
                policy.embeddingProvider(),
                policy.embeddingModel(),
                policy.semanticEmbeddingBatchSize());
        String requestedChunker = policy.requestedChunkerId();
        Optional<ProcessingComponentRef> pinnedActual = pinnedActualChunker(resolvedSnapshot);
        pinnedActual.ifPresent(component -> {
            ProcessingComponentRef available = chunkers.require(component.id()).component();
            if (!available.equals(component)) {
                throw new IllegalStateException(
                        "pinned actual chunker is unavailable: " + component);
            }
        });
        String actualChunker = pinnedActual
                .map(ProcessingComponentRef::id)
                .orElse(requestedChunker);
        if (!actualChunker.equals(requestedChunker)
                && !(SemanticVectorChunker.COMPONENT.id().equals(requestedChunker)
                        && RecursiveCharacterChunker.COMPONENT.id().equals(actualChunker))) {
            throw new IllegalArgumentException("resolved profile contains an unsupported fallback");
        }
        ChunkerOptions options = options(policy, actualChunker);
        List<ChunkedText> output;
        String fallbackCode = "";
        long chunkStartedAt = System.nanoTime();
        if (!actualChunker.equals(requestedChunker)) {
            output = chunkers.execute(
                    actualChunker,
                    new ChunkingRequest(
                            parsed.document(), tokenizer, Optional.empty()),
                    options);
            fallbackCode = "SEMANTIC_EMBEDDING_UNAVAILABLE";
        } else {
            try {
                output = chunkers.execute(
                        requestedChunker,
                        new ChunkingRequest(
                                parsed.document(), tokenizer, Optional.of(semanticEmbedding)),
                        options);
            } catch (SemanticEmbeddingUnavailableException
                    | SemanticEmbeddingInvocationException semanticFailure) {
                if (!SemanticVectorChunker.COMPONENT.id().equals(requestedChunker)) {
                    throw semanticFailure;
                }
                actualChunker = RecursiveCharacterChunker.COMPONENT.id();
                options = recursiveOptions(policy);
                fallbackCode = "SEMANTIC_EMBEDDING_UNAVAILABLE";
                output = chunkers.execute(
                        actualChunker,
                        new ChunkingRequest(parsed.document(), tokenizer, Optional.empty()),
                        options);
            }
        }
        // Measured before the limit check so a rejected document is still measured work; the
        // fallback path above is inside the same window on purpose, because a semantic chunker
        // failing over to the recursive one is time this document actually spent chunking.
        Duration chunkDuration =
                Duration.ofNanos(Math.max(0, System.nanoTime() - chunkStartedAt));
        if (output.size() > maximumChunks) {
            throw new RejectedSourceException(
                    "CHUNK_LIMIT_EXCEEDED",
                    "The ." + request.suffix() + " document exceeds its "
                            + maximumChunks + " chunk limit");
        }
        var actual = chunkers.require(actualChunker).component();
        Map<String, String> resolvedOptions = resolvedOptions(
                policy,
                resolvedOptions(options),
                fallbackCode,
                chunkManifestSha256(output));
        var profile = ResolvedDocumentProcessingProfile.resolve(
                parser.component(),
                parser.component(),
                chunkers.require(requestedChunker).component(),
                actual,
                tokenizer.component(),
                SemanticVectorChunker.COMPONENT.id().equals(requestedChunker)
                        ? Optional.of(semanticEmbedding.component())
                        : Optional.empty(),
                resolvedOptions,
                parsed.document().contentSha256());
        DocumentProcessingProfileSnapshot produced = new DocumentProcessingProfileSnapshot(
                profile.canonicalForm(), profile.profileSha256());
        resolvedSnapshot.ifPresent(pinned -> {
            if (!pinned.equals(produced)) {
                throw new ProcessingProfileMismatchException(
                        "processing output no longer matches the pinned resolved profile");
            }
        });
        return new ProcessedSourceDocument(
                parsed,
                output,
                profile,
                parseDuration,
                chunkDuration);
    }

    private ChunkerOptions options(
            RequestedProcessingPolicy policy,
            String chunkerId) {
        if (FixedTokenChunker.COMPONENT.id().equals(chunkerId)) {
            return new FixedTokenOptions(
                    policy.chunkSize(), policy.chunkOverlap(), null, false);
        }
        if (RecursiveCharacterChunker.COMPONENT.id().equals(chunkerId)) {
            return recursiveOptions(policy);
        }
        if (SemanticVectorChunker.COMPONENT.id().equals(chunkerId)) {
            return new SemanticVectorOptions(
                    policy.chunkSize(),
                    Integer.parseInt(policy.values().get("semantic.bufferSize")),
                    SemanticVectorOptions.BreakpointThreshold.valueOf(
                            policy.values().get("semantic.threshold")),
                    Double.parseDouble(policy.values().get("semantic.thresholdAmount")),
                    policy.values().get("semantic.sentenceSplitRegex"));
        }
        if (ParagraphSemanticChunker.COMPONENT.id().equals(chunkerId)) {
            return new ParagraphSemanticOptions(
                    policy.chunkSize(),
                    policy.chunkOverlap(),
                    policy.shortAnchorChars(),
                    policy.dropReferences(),
                    policy.referencePrefixes());
        }
        ProcessingComponentRef component = chunkers.require(chunkerId).component();
        throw new IllegalArgumentException(
                "third-party chunker " + component + " requires an explicit options resolver");
    }

    private RecursiveCharacterOptions recursiveOptions(RequestedProcessingPolicy policy) {
        return new RecursiveCharacterOptions(
                policy.chunkSize(),
                policy.chunkOverlap(),
                policy.recursiveSeparators(),
                policy.recursiveKeepSeparator());
    }

    private static Map<String, String> resolvedOptions(ChunkerOptions options) {
        if (options instanceof FixedTokenOptions fixed) {
            return Map.of(
                    "chunkTokenSize", Integer.toString(fixed.chunkTokenSize()),
                    "overlapTokenSize", Integer.toString(fixed.overlapTokenSize()),
                    "splitOnly", Boolean.toString(fixed.splitOnly()));
        }
        if (options instanceof RecursiveCharacterOptions recursive) {
            return Map.of(
                    "chunkTokenSize", Integer.toString(recursive.chunkTokenSize()),
                    "overlapTokenSize", Integer.toString(recursive.overlapTokenSize()),
                    "keepSeparator", Boolean.toString(recursive.keepSeparator()),
                    "separators", String.join("\\u001f", recursive.separators()));
        }
        if (options instanceof SemanticVectorOptions semantic) {
            return Map.of(
                    "chunkTokenSize", Integer.toString(semantic.chunkTokenSize()),
                    "bufferSize", Integer.toString(semantic.bufferSize()),
                    "threshold", semantic.threshold().name(),
                    "thresholdAmount", Double.toString(semantic.thresholdAmount()),
                    "sentenceSplitRegex", semantic.sentenceSplitRegex());
        }
        ParagraphSemanticOptions paragraph = (ParagraphSemanticOptions) options;
        return Map.of(
                "chunkTokenSize", Integer.toString(paragraph.chunkTokenSize()),
                "overlapTokenSize", Integer.toString(paragraph.overlapTokenSize()),
                "shortParagraphAnchorChars",
                        Integer.toString(paragraph.shortParagraphAnchorChars()),
                "dropReferences", Boolean.toString(paragraph.dropReferences()));
    }

    private static Map<String, String> resolvedOptions(
            RequestedProcessingPolicy policy,
            Map<String, String> actual,
            String fallbackCode,
            String chunkManifestSha256) {
        var resolved = new TreeMap<String, String>();
        resolved.putAll(policy.resolvedProfileOptions());
        actual.forEach((key, value) -> resolved.put("actual." + key, value));
        resolved.put("profile.schemaVersion", "2");
        resolved.put("fallback.code", fallbackCode);
        resolved.put("chunkManifest.schemaVersion", "1");
        resolved.put("chunkManifest.sha256", chunkManifestSha256);
        return Map.copyOf(resolved);
    }

    private static Optional<ProcessingComponentRef> pinnedActualChunker(
            Optional<DocumentProcessingProfileSnapshot> snapshot) {
        return snapshot.map(DocumentProcessingProfileSnapshot::canonicalForm)
                .map(canonical -> canonical.lines()
                        .filter(line -> line.startsWith("chunker.actual="))
                        .map(line -> line.substring("chunker.actual=".length()))
                        .map(DocumentProcessingEngine::componentRef)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "resolved profile is missing actual chunker identity")));
    }

    private static ProcessingComponentRef componentRef(String value) {
        int separator = value.lastIndexOf('@');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("resolved profile has malformed component identity");
        }
        return new ProcessingComponentRef(
                value.substring(0, separator), value.substring(separator + 1));
    }

    private static String chunkManifestSha256(List<ChunkedText> chunks) {
        StringBuilder canonical = new StringBuilder("schemaVersion=1\n");
        for (int index = 0; index < chunks.size(); index++) {
            ChunkedText chunk = chunks.get(index);
            canonical.append("chunk=").append(index).append('\n');
            appendField(canonical, "content", chunk.content());
            appendField(canonical, "heading", chunk.heading() == null ? "" : chunk.heading());
            canonical.append("tokens=").append(chunk.tokenCount()).append('\n');
            canonical.append("startChar=").append(chunk.provenance().startChar()).append('\n');
            canonical.append("endChar=").append(chunk.provenance().endChar()).append('\n');
            canonical.append("startPage=").append(chunk.provenance().startPage()).append('\n');
            canonical.append("endPage=").append(chunk.provenance().endPage()).append('\n');
            canonical.append("blocks=").append(chunk.provenance().blockIndexes()).append('\n');
            canonical.append("canonicalTextSha256=")
                    .append(chunk.provenance().canonicalTextSha256())
                    .append('\n');
        }
        return ResolvedDocumentProcessingProfile.sha256(canonical.toString());
    }

    private static void appendField(StringBuilder target, String key, String value) {
        target.append(key)
                .append(".length=")
                .append(value.length())
                .append('\n')
                .append(key)
                .append('=')
                .append(value)
                .append('\n');
    }
}
