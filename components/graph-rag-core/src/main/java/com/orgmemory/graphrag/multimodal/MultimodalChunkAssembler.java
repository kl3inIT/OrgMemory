package com.orgmemory.graphrag.multimodal;

import com.orgmemory.graphrag.chunking.TextTokenizer;
import java.util.Objects;

/** Renders successful outputs using the LightRAG labels and truncates descriptions only. */
public final class MultimodalChunkAssembler {

    private final TextTokenizer tokenizer;

    public MultimodalChunkAssembler(TextTokenizer tokenizer) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
    }

    public MultimodalDerivedChunk assemble(
            MultimodalSidecarItem item,
            MultimodalAnalysisOutcome.Success success,
            int tokenBudget) {
        Objects.requireNonNull(item, "item");
        Objects.requireNonNull(success, "success");
        if (!item.itemId().equals(success.itemId())
                || item.modality() != success.modality()) {
            throw new IllegalArgumentException(
                    "analysis success does not match the sidecar item");
        }
        if (tokenBudget <= 0) {
            throw new IllegalArgumentException("tokenBudget must be positive");
        }
        String fixed = fixedContent(success.content());
        if (tokenizer.count(fixed) > tokenBudget) {
            throw new IllegalArgumentException(
                    "multimodal chunk labels and required fields exceed the token budget");
        }
        String description = truncateDescription(
                fixed,
                success.content().description(),
                tokenBudget);
        String content = fixed + "\n" + description;
        return new MultimodalDerivedChunk(
                item.itemId(),
                item.modality(),
                content,
                success.evidenceScope(),
                item.blockIndex(),
                item.targetStartChar(),
                item.targetEndChar(),
                item.payload().contentSha256(),
                success.route(),
                success.cacheKey());
    }

    private String truncateDescription(
            String fixed,
            String description,
            int tokenBudget) {
        String candidate = fixed + "\n" + description;
        if (tokenizer.count(candidate) <= tokenBudget) {
            return description;
        }
        int low = 0;
        int high = description.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String prefix = description.substring(0, middle).stripTrailing();
            if (tokenizer.count(fixed + "\n" + prefix) <= tokenBudget) {
                low = middle;
            } else {
                high = middle - 1;
            }
        }
        String truncated = description.substring(0, low).stripTrailing();
        if (truncated.isEmpty()) {
            throw new IllegalArgumentException(
                    "multimodal description cannot fit the token budget");
        }
        return truncated;
    }

    private static String fixedContent(MultimodalAnalysisContent content) {
        return switch (content) {
            case MultimodalAnalysisContent.Image image ->
                    "[Image Name] " + image.name()
                            + "\n[Image Type] " + image.imageType();
            case MultimodalAnalysisContent.Table table ->
                    "[Table Name] " + table.name();
            case MultimodalAnalysisContent.Equation equation ->
                    equation.equation() + "\n[Equation Name] " + equation.name();
        };
    }
}
