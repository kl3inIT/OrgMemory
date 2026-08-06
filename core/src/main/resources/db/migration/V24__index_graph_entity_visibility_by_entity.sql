-- `loadVisibleEntityDegrees` resolves source/target visibility one entity at a
-- time. Production upgrades must pre-stage this index concurrently before the
-- application runs Flyway. Fresh databases are empty, so creating it here is
-- safe and keeps local/test bootstrap self-contained.
DO $$
BEGIN
    IF to_regclass('public.idx_projection_graph_entity_visible_by_entity') IS NULL THEN
        IF EXISTS (SELECT 1 FROM projection_graph_entity_contributions LIMIT 1) THEN
            RAISE EXCEPTION USING
                MESSAGE = 'idx_projection_graph_entity_visible_by_entity must be pre-staged',
                HINT = 'Run infrastructure/postgres-rag/prestage-graph-prerequisite-indexes.sql before deployment';
        END IF;

        CREATE INDEX idx_projection_graph_entity_visible_by_entity
            ON projection_graph_entity_contributions (
                batch_id,
                organization_id,
                entity_id,
                knowledge_asset_id
            );
    END IF;
END
$$;
