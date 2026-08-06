CREATE INDEX IF NOT EXISTS idx_graph_model_cache_namespace_operation_created
    ON graph_model_invocation_cache (
        organization_id,
        workspace,
        collection_name,
        operation,
        created_at DESC
    );

CREATE INDEX IF NOT EXISTS idx_graph_model_cache_operation_expires
    ON graph_model_invocation_cache (operation, expires_at);
