package com.orgmemory.integrations.graphrag.springai;

import com.orgmemory.graphrag.chunking.TextTokenizer;
import com.orgmemory.graphrag.extraction.LightRagEntityRelationExtractor;
import com.orgmemory.graphrag.extraction.LightRagExtractionPrompt;
import com.orgmemory.graphrag.model.ExtractionRequest;
import com.orgmemory.graphrag.model.ExtractionResult;
import com.orgmemory.graphrag.port.EntityRelationExtractor;
import java.util.Objects;
import org.springframework.ai.chat.model.ChatModel;

public final class SpringAiEntityRelationExtractor implements EntityRelationExtractor {

    public static final String PROMPT_VERSION = LightRagExtractionPrompt.VERSION;

    private final EntityRelationExtractor delegate;

    public SpringAiEntityRelationExtractor(String provider, ChatModel chatModel) {
        this(provider, chatModel, new JtokkitTextTokenizer("o200k_base"));
    }

    public SpringAiEntityRelationExtractor(
            String provider,
            ChatModel chatModel,
            TextTokenizer tokenizer) {
        delegate = new LightRagEntityRelationExtractor(
                new SpringAiExtractionModel(
                        requireText(provider, "provider"),
                        Objects.requireNonNull(chatModel, "chatModel")),
                Objects.requireNonNull(tokenizer, "tokenizer"));
    }

    @Override
    public ExtractionResult extract(ExtractionRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            if (!PROMPT_VERSION.equals(request.profile().promptVersion())) {
                throw new GraphExtractionException(
                        "Unsupported graph extraction prompt version: "
                                + request.profile().promptVersion());
            }
            return delegate.extract(request);
        } catch (GraphExtractionException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new GraphExtractionException(
                    "Structured entity-relation extraction failed",
                    exception);
        }
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
