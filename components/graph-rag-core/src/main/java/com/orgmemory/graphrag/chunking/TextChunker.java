package com.orgmemory.graphrag.chunking;

import com.orgmemory.graphrag.processing.ProcessingComponentRef;
import java.util.List;

public interface TextChunker<O extends ChunkerOptions> {

    ProcessingComponentRef component();

    Class<O> optionsType();

    List<ChunkedText> chunk(ChunkingRequest request, O options);
}
