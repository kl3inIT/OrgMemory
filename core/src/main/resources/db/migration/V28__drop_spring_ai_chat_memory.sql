-- Retire the second copy of an Assistant conversation.
--
-- V6 created this table for Spring AI's MessageWindowChatMemory alongside
-- assistant_conversation_messages, which already held the same questions and
-- answers with an organization, an actor, and foreign keys. This one had none
-- of those while holding raw message content, and consistency between the two
-- was kept by a second delete call in the delivery layer.
--
-- Nothing model-only ever lived here. Spring AI's JDBC repository filters tool
-- messages out before they arrive, the tool loop runs inside the memory advisor
-- so they never reach it at all, and permission-scoped grounding is rebuilt
-- into a request-local system message on every turn by design. Production held
-- only USER and ASSISTANT rows, and each was a copy of a transcript row.
--
-- Deliberately a separate migration from V26, which added turn_id: the reader
-- that replaces the memory advisor depends on turn identity, so the two changes
-- must be able to deploy and roll back independently.

DROP TABLE IF EXISTS public.spring_ai_chat_memory;
