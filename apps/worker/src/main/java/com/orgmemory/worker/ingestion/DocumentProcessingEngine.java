package com.orgmemory.worker.ingestion;

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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

/** Resolves one immutable parser/chunker snapshot and executes the full PR3 engine. */
@Component
final class DocumentProcessingEngine {

    private static final Set<String> LEGACY_SUFFIXES = Set.of(
            "txt", "md", "pdf", "docx", "pptx");
    private final SourceProcessingProperties properties;
    private final ParserRegistrySnapshot parsers;
    private final ChunkerRegistrySnapshot chunkers;
    private final JtokkitTextTokenizer tokenizer;

    DocumentProcessingEngine(
            SourceProcessingProperties properties,
            SourceDocumentReader legacyParser) {
        this.properties = properties;
        ClassLoader classLoader = DocumentProcessingEngine.class.getClassLoader();
        var nativeParser = new NativeSourceDocumentParser(legacyParser);
        this.parsers = new ParserRegistry()
                .register(new ParserSpec(
                        legacyParser.component(),
                        LEGACY_SUFFIXES,
                        true,
                        true,
                        "",
                        legacyParser))
                .register(new ParserSpec(
                        nativeParser.component(),
                        Set.of("md", "docx"),
                        true,
                        true,
                        "",
                        nativeParser))
                .register(new ParserSpec(
                        PassthroughParser.COMPONENT,
                        Set.of("txt", "md"),
                        false,
                        true,
                        "",
                        new PassthroughParser()))
                .register(new ParserSpec(
                        ReuseParser.COMPONENT,
                        Set.of("canonical"),
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
        this.tokenizer = new JtokkitTextTokenizer(properties.tokenizerEncoding());
    }

    ProcessedSourceDocument process(
            DocumentParseRequest request,
            EmbeddingModel embeddingModel) {
        ParserSpec parser = parsers.route(request.suffix(), properties.parserId());
        var parsed = parser.parser().parse(request);
        long minimumChunks = Math.ceilDiv(
                (long) tokenizer.count(parsed.document().content()),
                properties.chunkSize());
        if (minimumChunks > properties.maximumChunks()) {
            throw new RejectedSourceException(
                    "CHUNK_LIMIT_EXCEEDED",
                    "The document exceeds the configured chunk limit");
        }
        var semanticEmbedding = new SpringAiTextEmbeddingPort(
                embeddingModel,
                properties.embeddingProvider(),
                properties.embeddingModel(),
                properties.semanticEmbeddingBatchSize());
        String requestedChunker = properties.chunkerId();
        String actualChunker = requestedChunker;
        ChunkerOptions options = options(requestedChunker);
        Map<String, String> requestedOptions = resolvedOptions(options);
        List<ChunkedText> output;
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
            options = recursiveOptions();
            output = chunkers.execute(
                    actualChunker,
                    new ChunkingRequest(parsed.document(), tokenizer, Optional.empty()),
                    options);
        }
        if (output.size() > properties.maximumChunks()) {
            throw new RejectedSourceException(
                    "CHUNK_LIMIT_EXCEEDED",
                    "The document exceeds the configured chunk limit");
        }
        var actual = chunkers.require(actualChunker).component();
        Map<String, String> resolvedOptions = resolvedOptions(
                requestedOptions,
                resolvedOptions(options));
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
        return new ProcessedSourceDocument(parsed, output, profile);
    }

    private ChunkerOptions options(String chunkerId) {
        if (FixedTokenChunker.COMPONENT.id().equals(chunkerId)) {
            return new FixedTokenOptions(
                    properties.chunkSize(), properties.chunkOverlap(), null, false);
        }
        if (RecursiveCharacterChunker.COMPONENT.id().equals(chunkerId)) {
            return recursiveOptions();
        }
        if (SemanticVectorChunker.COMPONENT.id().equals(chunkerId)) {
            return new SemanticVectorOptions(
                    properties.chunkSize(),
                    1,
                    SemanticVectorOptions.BreakpointThreshold.PERCENTILE,
                    95,
                    SemanticVectorOptions.DEFAULT_SENTENCE_SPLIT_REGEX);
        }
        if (ParagraphSemanticChunker.COMPONENT.id().equals(chunkerId)) {
            return new ParagraphSemanticOptions(
                    properties.chunkSize(),
                    properties.chunkOverlap(),
                    100,
                    false,
                    List.of("references", "bibliography", "参考文献", "tài liệu tham khảo"));
        }
        ProcessingComponentRef component = chunkers.require(chunkerId).component();
        throw new IllegalArgumentException(
                "third-party chunker " + component + " requires an explicit options resolver");
    }

    private RecursiveCharacterOptions recursiveOptions() {
        return new RecursiveCharacterOptions(
                properties.chunkSize(),
                properties.chunkOverlap(),
                RecursiveCharacterOptions.DEFAULT_SEPARATORS,
                true);
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
            Map<String, String> requested,
            Map<String, String> actual) {
        var resolved = new TreeMap<String, String>();
        requested.forEach((key, value) -> resolved.put("requested." + key, value));
        actual.forEach((key, value) -> resolved.put("actual." + key, value));
        return Map.copyOf(resolved);
    }
}
