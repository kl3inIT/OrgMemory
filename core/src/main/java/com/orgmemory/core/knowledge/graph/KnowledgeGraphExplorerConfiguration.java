package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.retrieval.KnowledgeEvidenceScopeResolver;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceQuery;
import com.orgmemory.core.permission.PermissionAuditService;
import com.orgmemory.graphrag.export.GraphExportReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class KnowledgeGraphExplorerConfiguration {

    @Bean
    KnowledgeGraphExplorerService knowledgeGraphExplorerService(
            KnowledgeSpaceQuery spaces,
            RelationshipAuthorizationPort authorization,
            KnowledgeEvidenceScopeResolver evidenceScopes,
            GraphExportReader graphs,
            GraphExplorerProperties properties,
            PermissionAuditService audit) {
        return new KnowledgeGraphExplorerService(
                spaces,
                authorization,
                evidenceScopes,
                graphs,
                properties,
                audit);
    }
}
