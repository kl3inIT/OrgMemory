package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.parsing.CanonicalDocument;
import com.orgmemory.graphrag.parsing.DocumentBlock;
import com.orgmemory.graphrag.parsing.DocumentBlockKind;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Heading-aligned paragraph strategy with table-row splitting, long-block splitting, and
 * level-aware merging.
 *
 * <p>Parser adapters produce the typed block IR. Multimodal sidecar production remains an
 * effect boundary; adding sidecar artifacts must not change this strategy version's boundaries.
 */
public final class ParagraphSemanticChunker
        implements TextChunker<ParagraphSemanticOptions> {

    public static final ProcessingComponentRef COMPONENT =
            new ProcessingComponentRef("paragraph-semantic", "lightrag-v1.5.4");
    private static final double IDEAL_RATIO = 0.75;

    @Override
    public ProcessingComponentRef component() {
        return COMPONENT;
    }

    @Override
    public Class<ParagraphSemanticOptions> optionsType() {
        return ParagraphSemanticOptions.class;
    }

    @Override
    public List<ChunkedText> chunk(
            ChunkingRequest request,
            ParagraphSemanticOptions options) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(options, "options");
        CanonicalDocument document = request.document();
        if (!document.hasStructuredBlocks()) {
            return new RecursiveCharacterChunker().chunk(
                    request,
                    new RecursiveCharacterOptions(
                            options.chunkTokenSize(),
                            options.overlapTokenSize(),
                            RecursiveCharacterOptions.DEFAULT_SEPARATORS,
                            true));
        }

        List<Draft> initial = buildDrafts(request, options);
        if (initial.isEmpty()) {
            return new RecursiveCharacterChunker().chunk(
                    request,
                    new RecursiveCharacterOptions(
                            options.chunkTokenSize(),
                            options.overlapTokenSize(),
                            RecursiveCharacterOptions.DEFAULT_SEPARATORS,
                            true));
        }
        List<Draft> merged = mergeSmallBlocks(
                initial,
                request.tokenizer(),
                options.chunkTokenSize(),
                Math.max(1, (int) (options.chunkTokenSize() * IDEAL_RATIO)));
        List<ChunkedText> result = new ArrayList<>(merged.size());
        for (Draft draft : merged) {
            if (draft.content().isBlank()) {
                continue;
            }
            result.add(new ChunkedText(
                    result.size(),
                    draft.content(),
                    request.tokenizer().count(draft.content()),
                    draft.heading(),
                    ChunkProvenanceFactory.create(
                            document, draft.startChar(), draft.endChar())));
        }
        return List.copyOf(result);
    }

    private static List<Draft> buildDrafts(
            ChunkingRequest request,
            ParagraphSemanticOptions options) {
        CanonicalDocument document = request.document();
        List<Draft> output = new ArrayList<>();
        String[] headings = new String[6];
        int currentLevel = 1;
        boolean droppingReferences = false;
        for (DocumentBlock block : document.blocks()) {
            String text = document.text(block).strip();
            if (text.isEmpty()) {
                continue;
            }
            if (block.kind() == DocumentBlockKind.HEADING) {
                currentLevel = block.headingLevel();
                headings[currentLevel - 1] = text;
                for (int index = currentLevel; index < headings.length; index++) {
                    headings[index] = null;
                }
                droppingReferences = options.dropReferences()
                        && isReferenceHeading(text, options.referenceHeadingPrefixes())
                        && isTrailingHeading(document.blocks(), block.index());
                continue;
            }
            if (droppingReferences) {
                continue;
            }
            String heading = headingPath(headings);
            int tokenCount = request.tokenizer().count(text);
            List<Draft> fragments;
            if (tokenCount <= options.chunkTokenSize()) {
                fragments = List.of(new Draft(
                        text,
                        block.startChar(),
                        block.endChar(),
                        heading,
                        currentLevel,
                        block.kind() == DocumentBlockKind.TABLE ? TableRole.WHOLE : TableRole.NONE));
            } else if (block.kind() == DocumentBlockKind.TABLE) {
                fragments = splitTable(request, options, block, heading, currentLevel);
            } else {
                fragments = splitLongText(request, options, block, heading, currentLevel);
            }
            output.addAll(applyPartSuffixes(fragments));
        }
        return output;
    }

    private static List<Draft> splitLongText(
            ChunkingRequest request,
            ParagraphSemanticOptions options,
            DocumentBlock block,
            String heading,
            int level) {
        String source = request.document().text(block);
        List<Line> paragraphs = paragraphs(source);
        List<Integer> anchorIndexes = selectAnchors(
                source,
                paragraphs,
                request.tokenizer(),
                options.chunkTokenSize(),
                options.shortParagraphAnchorChars());
        if (!anchorIndexes.isEmpty()) {
            List<Draft> anchored = new ArrayList<>();
            int startParagraph = 0;
            String currentHeading = heading;
            for (int boundary = 0; boundary <= anchorIndexes.size(); boundary++) {
                int endParagraph = boundary < anchorIndexes.size()
                        ? anchorIndexes.get(boundary)
                        : paragraphs.size();
                Line first = paragraphs.get(startParagraph);
                Line last = paragraphs.get(endParagraph - 1);
                String group = source.substring(first.start(), last.end()).strip();
                if (request.tokenizer().count(group) <= options.chunkTokenSize()) {
                    anchored.add(new Draft(
                            group,
                            block.startChar() + first.start(),
                            block.startChar() + last.end(),
                            currentHeading,
                            level,
                            TableRole.NONE));
                } else {
                    anchored.addAll(splitTextRange(
                            request,
                            options,
                            block.startChar() + first.start(),
                            group,
                            currentHeading,
                            level));
                }
                if (boundary < anchorIndexes.size()) {
                    Line anchor = paragraphs.get(endParagraph);
                    String anchorHeading = source.substring(anchor.start(), anchor.end()).strip();
                    currentHeading = heading == null || heading.isBlank()
                            ? anchorHeading
                            : heading + " > " + anchorHeading;
                }
                startParagraph = endParagraph;
            }
            return anchored;
        }
        return splitTextRange(
                request,
                options,
                block.startChar(),
                source,
                heading,
                level);
    }

    private static List<Draft> splitTextRange(
            ChunkingRequest request,
            ParagraphSemanticOptions options,
            int absoluteStart,
            String source,
            String heading,
            int level) {
        CanonicalDocument local = CanonicalDocument.text(source);
        List<ChunkedText> chunks = new RecursiveCharacterChunker().chunk(
                new ChunkingRequest(local, request.tokenizer(), java.util.Optional.empty()),
                new RecursiveCharacterOptions(
                        options.chunkTokenSize(),
                        options.overlapTokenSize(),
                        RecursiveCharacterOptions.DEFAULT_SEPARATORS,
                        true));
        List<Draft> result = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            ChunkedText chunk = chunks.get(index);
            result.add(new Draft(
                    chunk.content(),
                    absoluteStart + chunk.provenance().startChar(),
                    absoluteStart + chunk.provenance().endChar(),
                    heading,
                    level,
                    TableRole.NONE));
        }
        return result;
    }

    private static List<Line> paragraphs(String source) {
        List<Line> result = new ArrayList<>();
        int cursor = 0;
        int index = 0;
        while (index <= source.length()) {
            boolean boundary = index == source.length()
                    || index + 1 < source.length()
                            && source.charAt(index) == '\n'
                            && source.charAt(index + 1) == '\n';
            if (boundary) {
                SourceSpan span = ChunkProvenanceFactory.trim(source, cursor, index);
                if (span != null) {
                    result.add(new Line(span.startChar(), span.endChar()));
                }
                if (index == source.length()) {
                    break;
                }
                while (index < source.length() && source.charAt(index) == '\n') {
                    index++;
                }
                cursor = index;
            } else {
                index++;
            }
        }
        return result;
    }

    private static List<Integer> selectAnchors(
            String source,
            List<Line> paragraphs,
            TextTokenizer tokenizer,
            int maximum,
            int maximumAnchorChars) {
        if (paragraphs.size() < 2) {
            return List.of();
        }
        int total = tokenizer.count(source);
        int ideal = Math.max(1, (int) (maximum * IDEAL_RATIO));
        int targetBlocks = Math.max(
                divideRoundingUp(total, ideal),
                divideRoundingUp(total, maximum));
        if (targetBlocks <= 1) {
            return List.of();
        }
        List<Anchor> candidates = new ArrayList<>();
        int cumulative = 0;
        for (int index = 0; index < paragraphs.size(); index++) {
            Line paragraph = paragraphs.get(index);
            if (index > 0
                    && paragraph.end() - paragraph.start() <= maximumAnchorChars) {
                candidates.add(new Anchor(index, cumulative));
            }
            cumulative += tokenizer.count(source.substring(paragraph.start(), paragraph.end()));
        }
        if (candidates.isEmpty()) {
            return List.of();
        }
        var selected = new LinkedHashSet<Integer>();
        for (int block = 1; block < targetBlocks && selected.size() < candidates.size(); block++) {
            double target = (double) total * block / targetBlocks;
            candidates.stream()
                    .filter(candidate -> !selected.contains(candidate.paragraphIndex()))
                    .min(Comparator.comparingDouble(
                            candidate -> Math.abs(candidate.tokenPosition() - target)))
                    .ifPresent(candidate -> selected.add(candidate.paragraphIndex()));
        }
        return selected.stream().sorted().toList();
    }

    private static int divideRoundingUp(int value, int divisor) {
        return (value + divisor - 1) / divisor;
    }

    private static List<Draft> splitTable(
            ChunkingRequest request,
            ParagraphSemanticOptions options,
            DocumentBlock block,
            String heading,
            int level) {
        String source = request.document().text(block);
        List<Line> lines = lines(source);
        if (lines.size() <= 1) {
            return splitLongText(request, options, block, heading, level);
        }
        Line header = lines.getFirst();
        List<Draft> result = new ArrayList<>();
        List<Line> current = new ArrayList<>();
        for (int index = 1; index < lines.size(); index++) {
            Line row = lines.get(index);
            List<Line> candidate = new ArrayList<>(current);
            candidate.add(row);
            String content = tableContent(source, header, candidate);
            if (!current.isEmpty()
                    && request.tokenizer().count(content) > options.chunkTokenSize()) {
                result.add(tableDraft(
                        request,
                        block,
                        source,
                        header,
                        current,
                        heading,
                        level,
                        result.isEmpty() ? TableRole.FIRST : TableRole.MIDDLE));
                current = new ArrayList<>();
            }
            current.add(row);
            String single = tableContent(source, header, current);
            if (request.tokenizer().count(single) > options.chunkTokenSize()) {
                String rowText = source.substring(row.start(), row.end());
                String headerText = source.substring(header.start(), header.end());
                int bodyLimit = options.chunkTokenSize()
                        - request.tokenizer().count(headerText + "\n");
                if (bodyLimit <= 0) {
                    throw new ChunkTokenLimitExceededException(
                            request.tokenizer().count(headerText),
                            options.chunkTokenSize());
                }
                CanonicalDocument rowDocument = CanonicalDocument.text(rowText);
                List<ChunkedText> forced = new RecursiveCharacterChunker().chunk(
                        new ChunkingRequest(
                                rowDocument, request.tokenizer(), java.util.Optional.empty()),
                        new RecursiveCharacterOptions(
                                bodyLimit,
                                0,
                                RecursiveCharacterOptions.DEFAULT_SEPARATORS,
                                true));
                for (ChunkedText chunk : forced) {
                    result.add(new Draft(
                            headerText + "\n" + chunk.content(),
                            block.startChar() + header.start(),
                            block.startChar() + row.start() + chunk.provenance().endChar(),
                            heading,
                            level,
                            result.isEmpty() ? TableRole.FIRST : TableRole.MIDDLE));
                }
                current.clear();
            }
        }
        if (!current.isEmpty()) {
            result.add(tableDraft(
                    request,
                    block,
                    source,
                    header,
                    current,
                    heading,
                    level,
                    result.isEmpty() ? TableRole.WHOLE : TableRole.LAST));
        }
        if (result.size() > 1 && result.getLast().role() == TableRole.MIDDLE) {
            Draft last = result.removeLast();
            result.add(last.withRole(TableRole.LAST));
        }
        return result;
    }

    private static Draft tableDraft(
            ChunkingRequest request,
            DocumentBlock block,
            String source,
            Line header,
            List<Line> rows,
            String heading,
            int level,
            TableRole role) {
        String content = tableContent(source, header, rows);
        int start = header.start();
        int end = rows.getLast().end();
        return new Draft(
                content,
                block.startChar() + start,
                block.startChar() + end,
                heading,
                level,
                role);
    }

    private static String tableContent(
            String source,
            Line header,
            List<Line> rows) {
        StringBuilder value =
                new StringBuilder(source.substring(header.start(), header.end())).append('\n');
        for (int index = 0; index < rows.size(); index++) {
            Line row = rows.get(index);
            if (index > 0) {
                value.append('\n');
            }
            value.append(source, row.start(), row.end());
        }
        return value.toString().strip();
    }

    private static List<Line> lines(String source) {
        List<Line> result = new ArrayList<>();
        int cursor = 0;
        for (int index = 0; index <= source.length(); index++) {
            if (index == source.length() || source.charAt(index) == '\n') {
                SourceSpan span = ChunkProvenanceFactory.trim(source, cursor, index);
                if (span != null) {
                    result.add(new Line(span.startChar(), span.endChar()));
                }
                cursor = index + 1;
            }
        }
        return result;
    }

    private static List<Draft> mergeSmallBlocks(
            List<Draft> input,
            TextTokenizer tokenizer,
            int maximum,
            int ideal) {
        List<Draft> result = new ArrayList<>();
        for (Draft draft : input) {
            if (result.isEmpty()) {
                result.add(draft);
                continue;
            }
            Draft previous = result.getLast();
            String mergedContent = previous.content() + "\n" + draft.content();
            boolean sameOrDescendant = draft.level() >= previous.level();
            boolean mergeableRoles = previous.role().canMergeForward()
                    && draft.role().canMergeBackward();
            if (mergeableRoles
                    && sameOrDescendant
                    && tokenizer.count(previous.content()) < ideal
                    && tokenizer.count(mergedContent) <= maximum) {
                result.set(
                        result.size() - 1,
                        new Draft(
                                mergedContent,
                                previous.startChar(),
                                draft.endChar(),
                                previous.heading(),
                                previous.level(),
                                TableRole.NONE));
            } else {
                result.add(draft);
            }
        }
        return result;
    }

    private static List<Draft> applyPartSuffixes(List<Draft> drafts) {
        List<Draft> result = new ArrayList<>(drafts.size());
        int part = 0;
        String previousHeading = null;
        for (Draft draft : drafts) {
            if (draft.heading() != null && draft.heading().equals(previousHeading)) {
                part++;
            } else {
                previousHeading = draft.heading();
                part = 1;
            }
            String base = draft.heading() == null ? "" : draft.heading();
            String heading = drafts.size() > 1
                    ? (base.isEmpty() ? "" : base + " ") + "[part " + part + "]"
                    : draft.heading();
            result.add(draft.withHeading(heading));
        }
        return result;
    }

    private static boolean isReferenceHeading(String value, List<String> prefixes) {
        String normalized = value.strip().toLowerCase(Locale.ROOT);
        return prefixes.stream()
                .map(prefix -> prefix.strip().toLowerCase(Locale.ROOT))
                .anyMatch(normalized::startsWith);
    }

    private static boolean isTrailingHeading(List<DocumentBlock> blocks, int headingIndex) {
        return headingIndex >= Math.max(0, blocks.size() - 8);
    }

    private static String headingPath(String[] headings) {
        return java.util.Arrays.stream(headings)
                .filter(Objects::nonNull)
                .reduce((left, right) -> left + " > " + right)
                .orElse(null);
    }

    private enum TableRole {
        NONE,
        WHOLE,
        FIRST,
        MIDDLE,
        LAST;

        boolean canMergeForward() {
            return this != FIRST && this != MIDDLE;
        }

        boolean canMergeBackward() {
            return this != MIDDLE && this != LAST;
        }
    }

    private record Draft(
            String content,
            int startChar,
            int endChar,
            String heading,
            int level,
            TableRole role) {

        private Draft {
            content = Objects.requireNonNull(content, "content").strip();
            if (content.isEmpty() || startChar < 0 || endChar <= startChar) {
                throw new IllegalArgumentException("paragraph draft must be non-empty");
            }
            Objects.requireNonNull(role, "role");
        }

        Draft withHeading(String value) {
            return new Draft(content, startChar, endChar, value, level, role);
        }

        Draft withRole(TableRole value) {
            return new Draft(content, startChar, endChar, heading, level, value);
        }
    }

    private record Line(int start, int end) {
    }

    private record Anchor(int paragraphIndex, int tokenPosition) {
    }
}
