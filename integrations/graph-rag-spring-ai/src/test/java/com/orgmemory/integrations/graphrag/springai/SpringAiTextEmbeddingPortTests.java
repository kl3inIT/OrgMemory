package com.orgmemory.integrations.graphrag.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.orgmemory.graphrag.chunking.SemanticEmbeddingInvocationException;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

class SpringAiTextEmbeddingPortTests {

    @Test
    void preservesOrderAcrossBoundedBatches() {
        RecordingEmbeddingModel model = new RecordingEmbeddingModel(false);
        var port = new SpringAiTextEmbeddingPort(model, "fixture", "model", 2);

        var result = port.embedAll(List.of("a", "bb", "ccc", "dddd", "eeeee"));

        assertEquals(List.of(2, 2, 1), model.batchSizes);
        assertEquals(
                List.of(1.0f, 2.0f, 3.0f, 4.0f, 5.0f),
                result.stream().map(vector -> vector.valueAt(0)).toList());
    }

    @Test
    void rejectsMixedDimensionsInsteadOfPublishingMisalignedVectors() {
        var port = new SpringAiTextEmbeddingPort(
                new RecordingEmbeddingModel(true),
                "fixture",
                "model",
                4);

        assertThrows(
                SemanticEmbeddingInvocationException.class,
                () -> port.embedAll(List.of("a", "bb")));
    }

    private static final class RecordingEmbeddingModel implements EmbeddingModel {

        private final boolean mixedDimensions;
        private final List<Integer> batchSizes = new ArrayList<>();

        private RecordingEmbeddingModel(boolean mixedDimensions) {
            this.mixedDimensions = mixedDimensions;
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            batchSizes.add(request.getInstructions().size());
            List<Embedding> embeddings = new ArrayList<>();
            for (int index = 0; index < request.getInstructions().size(); index++) {
                String text = request.getInstructions().get(index);
                float[] vector = mixedDimensions && index == 1
                        ? new float[] {text.length(), 1, 2}
                        : new float[] {text.length(), 1};
                embeddings.add(new Embedding(vector, index));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return new float[] {document.getText().length(), 1};
        }
    }
}
