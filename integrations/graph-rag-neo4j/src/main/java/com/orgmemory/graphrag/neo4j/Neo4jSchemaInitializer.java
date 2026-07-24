package com.orgmemory.graphrag.neo4j;

import java.util.List;
import java.util.Objects;

final class Neo4jSchemaInitializer {

    private static final List<String> STATEMENTS = List.of(
            """
            CREATE CONSTRAINT orgmemory_graph_entity_key IF NOT EXISTS
            FOR (node:OrgMemoryGraphEntity) REQUIRE node.key IS UNIQUE
            """,
            """
            CREATE CONSTRAINT orgmemory_graph_entity_contribution_key IF NOT EXISTS
            FOR (node:OrgMemoryGraphEntityContribution) REQUIRE node.key IS UNIQUE
            """,
            """
            CREATE CONSTRAINT orgmemory_graph_relation_contribution_key IF NOT EXISTS
            FOR (node:OrgMemoryGraphRelationContribution) REQUIRE node.key IS UNIQUE
            """,
            """
            CREATE CONSTRAINT orgmemory_graph_stage_key IF NOT EXISTS
            FOR (node:OrgMemoryGraphStage) REQUIRE node.key IS UNIQUE
            """,
            """
            CREATE INDEX orgmemory_graph_entity_lookup IF NOT EXISTS
            FOR (node:OrgMemoryGraphEntity) ON (node.batchId, node.entityId)
            """,
            """
            CREATE INDEX orgmemory_graph_entity_contribution_lookup IF NOT EXISTS
            FOR (node:OrgMemoryGraphEntityContribution)
            ON (node.batchId, node.entityId, node.knowledgeAssetId)
            """,
            """
            CREATE INDEX orgmemory_graph_relation_contribution_lookup IF NOT EXISTS
            FOR (node:OrgMemoryGraphRelationContribution)
            ON (node.batchId, node.relationId, node.knowledgeAssetId)
            """,
            """
            CREATE INDEX orgmemory_graph_revision_entity_contribution IF NOT EXISTS
            FOR (node:OrgMemoryGraphEntityContribution)
            ON (node.batchId, node.sourceRevisionId)
            """,
            """
            CREATE INDEX orgmemory_graph_revision_relation_contribution IF NOT EXISTS
            FOR (node:OrgMemoryGraphRelationContribution)
            ON (node.batchId, node.sourceRevisionId)
            """);

    private final Neo4jOperations operations;

    Neo4jSchemaInitializer(Neo4jOperations operations) {
        this.operations = Objects.requireNonNull(operations, "operations");
    }

    void initialize() {
        operations.writeWithoutResult(transaction ->
                STATEMENTS.forEach(statement -> transaction.run(statement).consume()));
    }
}
