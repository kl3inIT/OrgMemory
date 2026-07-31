package com.orgmemory.core.knowledge.graph;

import com.orgmemory.core.knowledge.KnowledgeEvidenceScopeResolver;
import com.orgmemory.core.authorization.RelationshipAuthorizationPort;
import com.orgmemory.core.knowledge.space.KnowledgeSpaceRepository;
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
            KnowledgeSpaceRepository spaces,
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
