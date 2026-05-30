-- ─────────────────────────────────────────────────────────────────────────────
-- 0003 — Row Level Security + indexes for public.reviews.
--
-- Why
-- ----
-- The customer review feature and the worker public-profile screen both read /
-- write the `reviews` table. Without explicit policies, RLS (if enabled) blocks
-- inserts/selects and the client sees a null / permission error on submit. This
-- migration:
--   1. Lets any authenticated user READ reviews (needed to render a worker's
--      public profile + rating breakdown to customers).
--   2. Lets a customer INSERT only their own review (customer_id = auth.uid()).
--   3. Lets the reviewed worker UPDATE the row to attach a reply
--      (worker_id = auth.uid()), and the author edit their own review.
--   4. Adds the indexes the worker-profile and "already reviewed?" lookups use.
--
-- Apply once via the Supabase SQL editor (or `supabase db push`). Idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Indexes for the common access paths.
CREATE INDEX IF NOT EXISTS reviews_worker_created_idx
    ON public.reviews (worker_id, created_at DESC);

CREATE INDEX IF NOT EXISTS reviews_booking_idx
    ON public.reviews (booking_id);

-- 2. Enable RLS and (re)create the policies.
ALTER TABLE public.reviews ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "reviews_select_authenticated" ON public.reviews;
CREATE POLICY "reviews_select_authenticated"
    ON public.reviews FOR SELECT
    TO authenticated
    USING (true);

DROP POLICY IF EXISTS "reviews_insert_own" ON public.reviews;
CREATE POLICY "reviews_insert_own"
    ON public.reviews FOR INSERT
    TO authenticated
    WITH CHECK (auth.uid() = customer_id);

-- Author can edit their review; assigned worker can attach/update a reply.
DROP POLICY IF EXISTS "reviews_update_party" ON public.reviews;
CREATE POLICY "reviews_update_party"
    ON public.reviews FOR UPDATE
    TO authenticated
    USING (auth.uid() = customer_id OR auth.uid() = worker_id)
    WITH CHECK (auth.uid() = customer_id OR auth.uid() = worker_id);

-- Refresh Postgrest schema cache.
NOTIFY pgrst, 'reload schema';
