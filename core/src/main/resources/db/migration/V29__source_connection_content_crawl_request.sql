-- A content crawl happens on the worker's own schedule: a fresh connection reads
-- everything on its next poll, and after that content is re-read once per interval while
-- the polls in between only re-check access. That cadence is held in the worker's memory
-- on purpose — losing it costs one extra crawl after a restart and nothing else.
--
-- What it left no room for is an administrator saying "now". Having changed which
-- channels to crawl, or invited the bot somewhere new, they had to wait out the interval
-- with no way to ask for the content to be re-read — the worker had no signal to act on,
-- because the one it uses never leaves its own process.
--
-- This is that signal, and the smallest one that crosses the process boundary: a single
-- timestamp the API writes and the worker reads. The worker remembers the last request it
-- served, so a newer timestamp forces one content crawl and is then spent. It is not the
-- cadence and does not replace it; it is an override the cadence already knows how to
-- honour, so no second durable schedule is introduced to keep the first one honest.
ALTER TABLE source_connections
    ADD COLUMN content_crawl_requested_at timestamptz;
