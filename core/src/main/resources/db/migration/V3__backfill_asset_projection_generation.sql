UPDATE projection_content_records AS content_record
SET metadata = (
    CASE
        WHEN content_record.metadata = '' THEN ''
        ELSE content_record.metadata || E'\n'
    END
    || 'YXNzZXRQcm9qZWN0aW9uR2VuZXJhdGlvbg:'
    || translate(
        replace(
            encode(
                convert_to(chunk.projection_generation::text, 'UTF8'),
                'base64'
            ),
            E'\n',
            ''
        ),
        '+/=',
        '-_'
    )
)
FROM knowledge_chunks AS chunk
WHERE content_record.content_kind = 'CHUNK'
  AND content_record.chunk_id = chunk.id
  AND content_record.organization_id = chunk.organization_id
  AND content_record.knowledge_asset_id = chunk.knowledge_asset_id
  AND content_record.source_revision_id = chunk.source_revision_id
  AND content_record.metadata !~
      '(^|\n)YXNzZXRQcm9qZWN0aW9uR2VuZXJhdGlvbg:';
