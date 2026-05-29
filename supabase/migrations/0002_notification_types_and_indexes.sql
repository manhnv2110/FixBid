-- ─────────────────────────────────────────────────────────────────────────────
-- 0002 — extend notification_type enum + supporting indexes for realtime feed.
--
-- Why
-- ----
-- The app's real-time notification feature introduces several new event kinds
-- that the worker/customer flows emit (cleaning schedule lifecycle, worker on
-- the way, etc). Postgres rejects any INSERT whose `type` is not part of the
-- `notification_type` enum, so we widen the enum to match the Kotlin
-- `NotificationType` model. We also add the indexes the realtime feed relies on
-- (per-user, newest first + unread badge count).
--
-- Apply once via the Supabase SQL editor (or `supabase db push`). Every
-- statement is idempotent so the file is safe to re-run.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Widen the enum. ADD VALUE IF NOT EXISTS is idempotent (Postgres 12+).
--    Each runs in its own implicit transaction; keep them as separate stmts.
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'booking_reminder';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'worker_on_the_way';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'job_started';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'job_completed';

-- 2. Indexes powering the notification list + unread badge.
CREATE INDEX IF NOT EXISTS notifications_user_created_idx
    ON public.notifications (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS notifications_unread_idx
    ON public.notifications (user_id)
    WHERE is_read = false;

-- 3. Make sure the table streams over Supabase Realtime so INSERTs reach the
--    client `postgresChangeFlow` subscriptions. Guarded against double-add.
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_publication_tables
        WHERE pubname = 'supabase_realtime'
          AND schemaname = 'public'
          AND tablename = 'notifications'
    ) THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE public.notifications;
    END IF;
END$$;

-- 4. Row Level Security: a user may only read / update their own notifications,
--    while any authenticated user may insert a notification addressed to
--    another user (e.g. a customer notifying the assigned worker). Tighten this
--    further with edge functions later if you move creation server-side.
ALTER TABLE public.notifications ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "notifications_select_own" ON public.notifications;
CREATE POLICY "notifications_select_own"
    ON public.notifications FOR SELECT
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "notifications_update_own" ON public.notifications;
CREATE POLICY "notifications_update_own"
    ON public.notifications FOR UPDATE
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "notifications_insert_authenticated" ON public.notifications;
CREATE POLICY "notifications_insert_authenticated"
    ON public.notifications FOR INSERT
    TO authenticated
    WITH CHECK (true);

-- Refresh Postgrest's schema cache so the new enum values are accepted.
NOTIFY pgrst, 'reload schema';
