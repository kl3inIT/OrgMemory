ALTER TABLE capability_assets
    ADD COLUMN tag_names varchar(500);

UPDATE capability_assets
SET tag_names = 'sales, follow-up, email'
WHERE tag_names IS NULL;
