package com.orgmemory.core.knowledge;

import com.orgmemory.core.authorization.RelationshipAuthorizationSetPort;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.observability.GraphRagEventSink;
import com.orgmemory.graphrag.query.LightRagQueryEngine;
import com.orgmemory.graphrag.storage.ProjectionPublicationStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnBean(LightRagQueryEngine.class)
class GraphRagKnowledgeRetrievalConfiguration {

    @Bean
    GraphRagKnowledgeRetrievalService graphRagKnowledgeRetrievalService(
            KnowledgeSearchAuthorizationService searchAuthorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            RelationshipAuthorizationSetPort authorization,
            SecureKnowledgeRetrievalStore canonicalEvidence,
            EmbeddingProfileRegistry embeddingProfiles,
            KnowledgeEmbeddingProperties embedding,
            ProjectionPublicationStore publications,
            LightRagQueryEngine engine,
            GraphRagRetrievalPolicy policy,
            PermissionAuditService audit,
            KnowledgeRetrievalProperties retrievalProperties,
            ObjectProvider<GraphRagEventSink> eventSinks) {
        return new GraphRagKnowledgeRetrievalService(
                searchAuthorization,
                evidenceScopes,
                authorization,
                canonicalEvidence,
                embeddingProfiles,
                embedding,
                publications,
                engine,
                policy,
                audit,
                retrievalProperties,
                eventSinks.getIfAvailable(() -> GraphRagEventSink.NO_OP));
    }
}
