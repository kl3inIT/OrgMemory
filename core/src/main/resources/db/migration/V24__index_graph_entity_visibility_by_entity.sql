-- `loadVisibleEntityDegrees` resolves source/target entity visibility one entity at
-- a time. Keep entity_id before the authorized asset filter so PostgreSQL can
-- perform a selective lookup rather than scanning every visible contribution in
-- the batch for each relation endpoint.
CREATE INDEX IF NOT EXISTS idx_projection_graph_entity_visible_by_entity
    ON projection_graph_entity_contributions (
        batch_id,
        organization_id,
        entity_id,
        knowledge_asset_id
    );
