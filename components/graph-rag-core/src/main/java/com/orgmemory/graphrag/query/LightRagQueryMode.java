package com.orgmemory.graphrag.query;

/** Query semantics exposed by LightRAG v1.5.4. */
public enum LightRagQueryMode {
    LOCAL,
    GLOBAL,
    HYBRID,
    NAIVE,
    MIX,
    BYPASS;

    public boolean usesGraph() {
        return this == LOCAL || this == GLOBAL || this == HYBRID || this == MIX;
    }

    public boolean usesEntitySeeds() {
        return this == LOCAL || this == HYBRID || this == MIX;
    }

    public boolean usesRelationSeeds() {
        return this == GLOBAL || this == HYBRID || this == MIX;
    }

    public boolean usesChunkSeeds() {
        return this == NAIVE || this == MIX;
    }
}
