-- ─────────────────────────────────────────────────────────────────────────────
-- 0010 — messages: ensure Realtime pipeline is fully operational.
--
-- Root cause of the "not real-time" bug:
-- ----------------------------------------
-- Even after migration 0009 added `recipient_id` and simplified RLS policies,
-- Supabase Realtime might still silently drop events because:
--
-- 1. The `supabase_realtime` publication must EXPLICITLY include the `messages`
--    table. Without this, no CDC events flow to the Realtime server.
--
-- 2. `REPLICA IDENTITY FULL` must be set on the table for UPDATE events to
--    carry full row data (needed so the client can decode `decodeRecord()` on
--    Update actions — without it, UPDATE only sends the changed columns and the
--    old row is empty).
--
-- 3. The Realtime system bypasses RLS by default when broadcasting events to
--    subscribed clients. However, if the Supabase project has
--    "Enforce RLS on Realtime" enabled in settings, the RLS policy applies to
--    WebSocket delivery as well. Migration 0009 already simplified those
--    policies to use simple column comparisons, but we double-check here.
--
-- Apply this in the Supabase SQL editor or via `supabase db push`.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Ensure messages is in the supabase_realtime publication
--    (idempotent — safe to run multiple times)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename  = 'messages'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.messages;
        RAISE NOTICE 'Added messages to supabase_realtime publication.';
    ELSE
        RAISE NOTICE 'messages already in supabase_realtime publication.';
    END IF;
END$$;

-- 2. Ensure REPLICA IDENTITY FULL so UPDATE events carry all columns
--    This is required for the client to call decodeRecord() on Update events
--    and for UPDATE-based read-receipt delivery.
ALTER TABLE public.messages REPLICA IDENTITY FULL;

-- 3. Re-confirm the simplified RLS policies from 0009 are in place
--    (re-creates them idempotently — safe if 0009 was already applied)
DROP POLICY IF EXISTS "messages_select_participant" ON public.messages;
CREATE POLICY "messages_select_participant"
    ON public.messages FOR SELECT
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = recipient_id);

DROP POLICY IF EXISTS "messages_update_participant" ON public.messages;
CREATE POLICY "messages_update_participant"
    ON public.messages FOR UPDATE
    TO authenticated
    USING (auth.uid() = sender_id OR auth.uid() = recipient_id);

-- 4. Force a schema cache reload so PostgREST picks up any column changes
NOTIFY pgrst, 'reload schema';
