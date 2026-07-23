package com.orgmemory.graphrag.query;

import java.util.List;

public enum RetrievalStrategy {
    CHUNK_ONLY(List.of(RetrievalChannel.CHUNK)),
    ENTITY_ONLY(List.of(RetrievalChannel.ENTITY)),
    RELATION_ONLY(List.of(RetrievalChannel.RELATION)),
    SECURE_HYBRID(List.of(
            RetrievalChannel.ENTITY,
            RetrievalChannel.RELATION)),
    SECURE_MIX(List.of(
            RetrievalChannel.ENTITY,
            RetrievalChannel.RELATION,
            RetrievalChannel.CHUNK));

    private final List<RetrievalChannel> channels;

    RetrievalStrategy(List<RetrievalChannel> channels) {
        this.channels = channels;
    }

    public List<RetrievalChannel> channels() {
        return channels;
    }

    public boolean includes(RetrievalChannel channel) {
        return channels.contains(channel);
    }
}
