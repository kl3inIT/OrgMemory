package com.orgmemory.api.assistant;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.orgmemory.core.knowledge.retrieval.CanonicalHybridKnowledgeSearch;
import com.orgmemory.core.knowledge.retrieval.GraphRagKnowledgeRetrievalService;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class AssistantConfigurationTests {

    private final AssistantConfiguration configuration = new AssistantConfiguration();
    private final CanonicalHybridKnowledgeSearch canonical =
            mock(CanonicalHybridKnowledgeSearch.class);
    private final GraphRagKnowledgeRetrievalService graph =
            mock(GraphRagKnowledgeRetrievalService.class);

    @Test
    void selectsGraphRagWithoutAnImplicitFallback() {
        ObjectProvider<GraphRagKnowledgeRetrievalService> graphs = graphProvider(graph);

        assertSame(
                graph,
                configuration.permissionAwareKnowledgeSearch(
                        canonical,
                        graphs,
                        properties(AssistantProperties.RetrievalEngine.GRAPH_RAG)));
    }

    @Test
    void failsFastWhenGraphRagWasSelectedButIsUnavailable() {
        ObjectProvider<GraphRagKnowledgeRetrievalService> graphs = graphProvider(null);

        assertThrows(
                IllegalStateException.class,
                () -> configuration.permissionAwareKnowledgeSearch(
                        canonical,
                        graphs,
                        properties(AssistantProperties.RetrievalEngine.GRAPH_RAG)));
    }

    @Test
    void canonicalHybridIsAnExplicitEngineChoice() {
        assertSame(
                canonical,
                configuration.permissionAwareKnowledgeSearch(
                        canonical,
                        graphProvider(null),
                        properties(AssistantProperties.RetrievalEngine.CANONICAL_HYBRID)));
    }

    private static AssistantProperties properties(AssistantProperties.RetrievalEngine engine) {
        return new AssistantProperties(
                Duration.ofSeconds(15),
                Duration.ofMinutes(2),
                engine,
                8,
                32,
                Duration.ofSeconds(5));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<GraphRagKnowledgeRetrievalService> graphProvider(
            GraphRagKnowledgeRetrievalService graph) {
        ObjectProvider<GraphRagKnowledgeRetrievalService> provider =
                mock(ObjectProvider.class);
        when(provider.getIfAvailable(any())).thenAnswer(invocation -> {
            Supplier<GraphRagKnowledgeRetrievalService> fallback =
                    invocation.getArgument(0);
            return graph == null ? fallback.get() : graph;
        });
        return provider;
    }
}
