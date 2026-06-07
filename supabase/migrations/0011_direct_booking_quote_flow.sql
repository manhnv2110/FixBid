-- ─────────────────────────────────────────────────────────────────────────────
-- 0011 — Direct booking quote flow.
--
-- Why
-- ----
-- Previously the direct-booking lifecycle skipped the price-negotiation step:
-- the worker hit "Nhận đơn" and the booking jumped straight to
-- AWAITING_PAYMENT with `agreed_price = NULL`, which broke the payment screen
-- (PaymentViewModel returns early on a null price). We now insert a QUOTED
-- stage between PENDING and AWAITING_PAYMENT so the worker proposes a price
-- and the customer explicitly accepts/rejects it before paying.
--
-- New columns on `bookings`:
--   • quoted_price                       — price the worker proposed (VND)
--   • quote_message                      — worker's note explaining the price
--   • quoted_at                          — when the quote was sent
--   • quote_estimated_duration_hours    — duration the worker promises
--
-- New booking_status enum value: `quoted`.
-- New notification_type enum values: `booking_quoted`, `booking_quote_accepted`,
-- `booking_quote_rejected`.
--
-- Apply once via Supabase SQL editor or `supabase db push`. Idempotent.
-- ─────────────────────────────────────────────────────────────────────────────

-- 1. Booking columns ----------------------------------------------------------
ALTER TABLE public.bookings
    ADD COLUMN IF NOT EXISTS quoted_price numeric
        CHECK (quoted_price IS NULL OR quoted_price >= 0::numeric);
ALTER TABLE public.bookings
    ADD COLUMN IF NOT EXISTS quote_message text;
ALTER TABLE public.bookings
    ADD COLUMN IF NOT EXISTS quoted_at timestamp with time zone;
ALTER TABLE public.bookings
    ADD COLUMN IF NOT EXISTS quote_estimated_duration_hours numeric
        CHECK (quote_estimated_duration_hours IS NULL OR quote_estimated_duration_hours > 0::numeric);

-- 2. Booking status enum: add QUOTED ------------------------------------------
-- ADD VALUE IF NOT EXISTS is idempotent (Postgres 12+).
ALTER TYPE public.booking_status ADD VALUE IF NOT EXISTS 'quoted';

-- 3. Notification type enum: add the three quote events -----------------------
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'booking_quoted';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'booking_quote_accepted';
ALTER TYPE public.notification_type ADD VALUE IF NOT EXISTS 'booking_quote_rejected';

-- 4. Refresh PostgREST so the new enum values + columns are accepted. ---------
NOTIFY pgrst, 'reload schema';
