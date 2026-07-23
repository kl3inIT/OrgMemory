package com.orgmemory.graphrag.chunking;

/** Java ServiceLoader extension point for third-party chunkers. */
public interface ChunkerPlugin {

    void register(ChunkerRegistry registry);
}
