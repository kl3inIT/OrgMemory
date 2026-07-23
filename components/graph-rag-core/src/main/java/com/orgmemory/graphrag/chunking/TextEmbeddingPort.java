package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.model.FloatVector;
import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.List;

/** Batched semantic embedding effect used by the vector chunker. */
public interface TextEmbeddingPort {

    ProcessingComponentRef component();

    List<FloatVector> embedAll(List<String> texts);
}
