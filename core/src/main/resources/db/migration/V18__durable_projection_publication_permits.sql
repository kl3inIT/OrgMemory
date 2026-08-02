ALTER TABLE graph_index_jobs
    ADD COLUMN claim_epoch bigint NOT NULL DEFAULT 0,
    ADD COLUMN publication_permit_id uuid,
    ADD COLUMN publication_permit_batch_id uuid,
    ADD COLUMN publication_permit_claim_epoch bigint,
    ADD COLUMN publication_permit_issued_at timestamp with time zone;

ALTER TABLE projection_batches
    ADD COLUMN expected_previous_batch_id uuid,
    ADD COLUMN claim_epoch bigint NOT NULL DEFAULT 0,
    ADD COLUMN commit_permit_id uuid,
    ADD COLUMN commit_permit_claim_epoch bigint,
    ADD COLUMN commit_permit_issued_at timestamp with time zone;

CREATE UNIQUE INDEX uq_projection_batches_commit_permit
    ON projection_batches (commit_permit_id)
    WHERE commit_permit_id IS NOT NULL;
