ALTER TABLE capability_assets
    ADD COLUMN ai_tool varchar(255);

UPDATE capability_assets
SET ai_tool = 'ChatGPT'
WHERE ai_tool IS NULL;
