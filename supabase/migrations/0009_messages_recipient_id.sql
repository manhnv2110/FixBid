-- ─────────────────────────────────────────────────────────────────────────────
-- 0009 — messages table: recipient_id + Realtime & RLS simplification.
--
-- Why
-- ----
-- Two-sided realtime chat was not working because the SELECT policy for `messages`
-- contained an EXISTS check query on the `conversations` table.
-- Supabase Realtime does NOT support subqueries/joins in RLS policies, silences
-- all events if they exist.
-- To solve this, we add a `recipient_id` column to `messages` so that RLS can
-- check permissions through simple column matching (auth.uid() = sender_id OR auth.uid() = recipient_id).
-- We also alter the replica identity to FULL so UPDATE events (such as read receipts)
-- contain all column values needed for filters.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Add recipient_id column (initially nullable to allow backfill)
ALTER TABLE public.messages ADD COLUMN IF NOT EXISTS recipient_id UUID REFERENCES auth.users(id);

-- 2. Clean up orphaned messages where parent conversation no longer exists
DELETE FROM public.messages m 
WHERE NOT EXISTS (
    SELECT 1 FROM public.conversations c 
    WHERE c.id = m.conversation_id
);

-- 3. Backfill recipient_id for existing messages using the conversations table
UPDATE public.messages m
SET recipient_id = COALESCE(
    CASE
        WHEN m.sender_id = c.customer_id THEN c.worker_id::uuid
        ELSE c.customer_id::uuid
    END,
    m.sender_id
)
FROM public.conversations c
WHERE m.conversation_id = c.id;

-- 4. In case any nulls still remain, fill with sender_id as fallback
UPDATE public.messages SET recipient_id = sender_id WHERE recipient_id IS NULL;

-- 5. Make recipient_id NOT NULL now that it is fully backfilled
ALTER TABLE public.messages ALTER COLUMN recipient_id SET NOT NULL;

-- 6. Simplify RLS policies to avoid subqueries/joins for Realtime support
DROP POLICY IF EXISTS "messages_select_participant" ON public.messages;
CREATE POLICY "messages_select_participant"
    ON public.messages FOR SELECT
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = recipient_id);

DROP POLICY IF EXISTS "messages_insert_participant" ON public.messages;
CREATE POLICY "messages_insert_participant"
    ON public.messages FOR INSERT
    TO authenticated
    WITH CHECK (
        sender_id = auth.uid() 
        AND (auth.uid() = sender_id OR auth.uid() = recipient_id)
    );

DROP POLICY IF EXISTS "messages_update_participant" ON public.messages;
CREATE POLICY "messages_update_participant"
    ON public.messages FOR UPDATE
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = recipient_id);

-- 7. Set replica identity to FULL so that UPDATE events contain the full row data
ALTER TABLE public.messages REPLICA IDENTITY FULL;

-- 8. Refresh schema cache
NOTIFY pgrst, 'reload schema';
