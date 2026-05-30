-- ─────────────────────────────────────────────────────────────────────────────
-- 0005 — conversations + messages: realtime + RLS for both parties.
--
-- Why
-- ----
-- Two-sided realtime chat needs:
--   1. The `messages` (and `conversations`) tables streaming over Supabase
--      Realtime so new messages reach BOTH the customer and the worker live.
--   2. RLS that lets either participant of a conversation read/write it — the
--      worker side previously couldn't see conversations addressed to them.
--
-- Apply once via the Supabase SQL editor (or `supabase db push`). Idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Realtime publication ----------------------------------------------------
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime' AND schemaname = 'public' AND tablename = 'conversations'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.conversations;
    END IF;
END$$;

-- 2. Indexes for the conversation list + message thread queries.
CREATE INDEX IF NOT EXISTS conversations_customer_idx ON public.conversations (customer_id);
CREATE INDEX IF NOT EXISTS conversations_worker_idx   ON public.conversations (worker_id);
CREATE INDEX IF NOT EXISTS messages_conversation_created_idx
    ON public.messages (conversation_id, created_at DESC);

-- 3. RLS: conversations — a participant (customer OR worker) may read/insert/update.
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "conversations_select_participant" ON public.conversations;
CREATE POLICY "conversations_select_participant"
    ON public.conversations FOR SELECT
    TO authenticated
    USING (auth.uid() = customer_id OR auth.uid() = worker_id);

DROP POLICY IF EXISTS "conversations_insert_participant" ON public.conversations;
CREATE POLICY "conversations_insert_participant"
    ON public.conversations FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = customer_id OR auth.uid() = worker_id);

DROP POLICY IF EXISTS "conversations_update_participant" ON public.conversations;
CREATE POLICY "conversations_update_participant"
    ON public.conversations FOR UPDATE
    TO authenticated
    USING (auth.uid() = customer_id OR auth.uid() = worker_id)
    WITH CHECK (auth.uid() = customer_id OR auth.uid() = worker_id);

-- 4. RLS: messages — only participants of the parent conversation.
ALTER TABLE public.messages ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "messages_select_participant" ON public.messages;
CREATE POLICY "messages_select_participant"
    ON public.messages FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id
              AND (c.customer_id = auth.uid() OR c.worker_id = auth.uid())
        )
    );

-- Sender must be the authenticated user AND a participant of the conversation.
DROP POLICY IF EXISTS "messages_insert_participant" ON public.messages;
CREATE POLICY "messages_insert_participant"
    ON public.messages FOR INSERT
    TO authenticated
    WITH CHECK (
        sender_id = auth.uid()
        AND EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id
              AND (c.customer_id = auth.uid() OR c.worker_id = auth.uid())
        )
    );

-- Either participant may update (used to flip is_read on received messages).
DROP POLICY IF EXISTS "messages_update_participant" ON public.messages;
CREATE POLICY "messages_update_participant"
    ON public.messages FOR UPDATE
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.conversations c
            WHERE c.id = messages.conversation_id
              AND (c.customer_id = auth.uid() OR c.worker_id = auth.uid())
        )
    );

NOTIFY pgrst, 'reload schema';
