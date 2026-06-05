-- ─── fcm_tokens: schema for multi-device push ───────────────────────────────
--
-- The client upserts (user_id, token) on every app start. PostgREST upsert
-- requires a unique constraint to resolve conflicts — the original table only
-- had a uuid PK, so the upsert silently inserted duplicates (or, when it
-- collided on the implicit PK, left stale rows).
--
-- Changes:
--   1. UNIQUE (token) so each device token has one row. If a user signs out
--      and another signs in on the same device, the row's user_id is
--      overwritten. This matches FCM's "one token = one app install" model.
--   2. updated_at column kept fresh by trigger, used by the cleanup job and
--      the edge function for diagnostics.
--   3. RLS so a user can only see/manage their own token rows.

ALTER TABLE public.fcm_tokens
    ADD COLUMN IF NOT EXISTS updated_at timestamp with time zone NOT NULL DEFAULT now();

-- Drop pre-existing duplicates before adding the unique constraint, keeping
-- the most recent row per token. Idempotent.
DELETE FROM public.fcm_tokens a
USING public.fcm_tokens b
WHERE a.token = b.token
  AND a.created_at < b.created_at;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fcm_tokens_token_key'
          AND conrelid = 'public.fcm_tokens'::regclass
    ) THEN
        ALTER TABLE public.fcm_tokens
            ADD CONSTRAINT fcm_tokens_token_key UNIQUE (token);
    END IF;
END$$;

-- Auto-update updated_at on row updates.
CREATE OR REPLACE FUNCTION public.fn_fcm_tokens_touch()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_fcm_tokens_touch ON public.fcm_tokens;
CREATE TRIGGER trg_fcm_tokens_touch
BEFORE UPDATE ON public.fcm_tokens
FOR EACH ROW EXECUTE FUNCTION public.fn_fcm_tokens_touch();

-- Indexes used by the push sender.
CREATE INDEX IF NOT EXISTS fcm_tokens_user_idx
    ON public.fcm_tokens (user_id);

-- ─── Row-Level Security ──────────────────────────────────────────────────────
ALTER TABLE public.fcm_tokens ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "fcm_tokens_select_own" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_select_own"
    ON public.fcm_tokens FOR SELECT
    TO authenticated
    USING (auth.uid() = user_id);

DROP POLICY IF EXISTS "fcm_tokens_insert_own" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_insert_own"
    ON public.fcm_tokens FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "fcm_tokens_update_own" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_update_own"
    ON public.fcm_tokens FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "fcm_tokens_delete_own" ON public.fcm_tokens;
CREATE POLICY "fcm_tokens_delete_own"
    ON public.fcm_tokens FOR DELETE
    TO authenticated
    USING (auth.uid() = user_id);

-- Refresh PostgREST schema cache.
NOTIFY pgrst, 'reload schema';
