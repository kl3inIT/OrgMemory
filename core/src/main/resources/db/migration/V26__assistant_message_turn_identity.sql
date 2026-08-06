-- Give an Assistant turn an explicit identity.
--
-- A turn writes its USER row in beginTurn and its ASSISTANT row in completeTurn,
-- in separate transactions. Two concurrent turns in one conversation can
-- therefore persist as U1, U2, A2, A1, which no ordering heuristic over
-- sequence_id pairs correctly. turn_id records the pairing the writers already
-- know instead of asking a later reader to infer it.
--
-- Rows written before this migration stay NULL: they remain visible in the
-- product transcript and are ineligible as model context, because their pairing
-- cannot be recovered after the fact.

ALTER TABLE public.assistant_conversation_messages
    ADD COLUMN turn_id uuid;

-- One USER and one ASSISTANT per turn. Partial so the legacy NULL rows, which
-- carry no pairing to protect, are exempt rather than colliding with each other.
CREATE UNIQUE INDEX idx_assistant_conversation_message_turn_role
    ON public.assistant_conversation_messages (turn_id, role)
    WHERE turn_id IS NOT NULL;
