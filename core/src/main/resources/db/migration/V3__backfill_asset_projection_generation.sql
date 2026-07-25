UPDATE projection_content_records AS content_record
SET metadata = (
    content_record.metadata::jsonb
    || jsonb_build_object(
        'assetProjectionGeneration',
        chunk.projection_generation
    )
)::text
FROM knowledge_chunks AS chunk
WHERE content_record.content_kind = 'CHUNK'
  AND content_record.chunk_id = chunk.id
  AND content_record.organization_id = chunk.organization_id
  AND content_record.knowledge_asset_id = chunk.knowledge_asset_id
  AND content_record.source_revision_id = chunk.source_revision_id
  AND NOT (
      content_record.metadata::jsonb
      ? 'assetProjectionGeneration'
  );
