-- ─────────────────────────────────────────────────────────────────────────────
-- 0004 — worker_profiles: RLS, auto-create row, backfill.
--
-- Why
-- ----
-- Two bugs both trace back to the `worker_profiles` row:
--   1. Workers had no row + no way to edit professional info → empty profile.
--   2. Customers tapping a worker hit "không tải được hồ sơ" because RLS (if on)
--      blocked reading another user's worker_profiles row, OR the row was absent.
--
-- This migration:
--   A. Enables RLS with sensible policies:
--        - anyone authenticated can READ worker profiles (needed for the public
--          profile + worker discovery),
--        - a worker can INSERT/UPDATE only their own row (user_id = auth.uid()).
--   B. Auto-creates a worker_profiles row whenever a profile with role='worker'
--      is created (or its role changes to worker), so the row always exists.
--   C. Backfills rows for any existing workers that are missing one.
--
-- Apply once via the Supabase SQL editor (or `supabase db push`). Idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

-- A. Row Level Security ------------------------------------------------------
ALTER TABLE public.worker_profiles ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "worker_profiles_select_authenticated" ON public.worker_profiles;
CREATE POLICY "worker_profiles_select_authenticated"
    ON public.worker_profiles FOR SELECT
    TO authenticated
    USING (true);

DROP POLICY IF EXISTS "worker_profiles_insert_own" ON public.worker_profiles;
CREATE POLICY "worker_profiles_insert_own"
    ON public.worker_profiles FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = user_id);

DROP POLICY IF EXISTS "worker_profiles_update_own" ON public.worker_profiles;
CREATE POLICY "worker_profiles_update_own"
    ON public.worker_profiles FOR UPDATE
    TO authenticated
    USING (auth.uid() = user_id)
    WITH CHECK (auth.uid() = user_id);

-- B. Auto-create a worker_profiles row for workers ---------------------------
CREATE OR REPLACE FUNCTION public.ensure_worker_profile()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
    IF NEW.role = 'worker' THEN
        INSERT INTO public.worker_profiles (user_id)
        VALUES (NEW.id)
        ON CONFLICT (user_id) DO NOTHING;
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS profiles_ensure_worker_profile ON public.profiles;
CREATE TRIGGER profiles_ensure_worker_profile
    AFTER INSERT OR UPDATE OF role
    ON public.profiles
    FOR EACH ROW
    EXECUTE FUNCTION public.ensure_worker_profile();

-- C. Backfill existing workers ------------------------------------------------
INSERT INTO public.worker_profiles (user_id)
SELECT p.id
FROM public.profiles p
WHERE p.role = 'worker'
  AND NOT EXISTS (
      SELECT 1 FROM public.worker_profiles wp WHERE wp.user_id = p.id
  );

-- Refresh Postgrest schema cache.
NOTIFY pgrst, 'reload schema';
