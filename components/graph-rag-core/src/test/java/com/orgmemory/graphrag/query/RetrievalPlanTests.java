package com.orgmemory.graphrag.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RetrievalPlanTests {

    @Test
    void secureMixCombinesAllInternalChannels() {
        RetrievalPlan plan = RetrievalPlan.secureMixDefaults();

        assertEquals(
                List.of(RetrievalChannel.ENTITY, RetrievalChannel.RELATION, RetrievalChannel.CHUNK),
                plan.channels());
        assertEquals(RetrievalStrategy.SECURE_MIX, plan.strategy());
        assertEquals(6_000, plan.contextBudget().maxEntityTokens());
        assertEquals(8_000, plan.contextBudget().maxRelationTokens());
        assertEquals(30_000, plan.contextBudget().maxTotalTokens());
    }

    @Test
    void strategiesComposeChannelsWithoutChangingThePlanContract() {
        assertEquals(
                List.of(RetrievalChannel.CHUNK),
                RetrievalPlan.defaults(RetrievalStrategy.CHUNK_ONLY).channels());
        assertEquals(
                List.of(RetrievalChannel.ENTITY),
                RetrievalPlan.defaults(RetrievalStrategy.ENTITY_ONLY).channels());
        assertEquals(
                List.of(RetrievalChannel.RELATION),
                RetrievalPlan.defaults(RetrievalStrategy.RELATION_ONLY).channels());
        assertEquals(
                List.of(RetrievalChannel.ENTITY, RetrievalChannel.RELATION),
                RetrievalPlan.defaults(RetrievalStrategy.SECURE_HYBRID).channels());
        assertEquals(
                List.of(RetrievalChannel.ENTITY, RetrievalChannel.RELATION, RetrievalChannel.CHUNK),
                RetrievalPlan.defaults(RetrievalStrategy.SECURE_MIX).channels());
    }

    @Test
    void givesChunksOnlyTheDynamicRemainder() {
        SecureContextBudget budget = SecureContextBudget.lightRagCompatibleDefaults();

        int available = budget.availableChunkTokens(new ContextTokenUsage(
                1_000,
                500,
                4_000,
                6_000));

        assertEquals(18_300, available);
        assertEquals(
                0,
                budget.availableChunkTokens(new ContextTokenUsage(20_000, 3_000, 3_000, 4_000)));
        assertThrows(
                IllegalArgumentException.class,
                () -> budget.availableChunkTokens(new ContextTokenUsage(0, 0, 6_001, 0)));
    }
}
