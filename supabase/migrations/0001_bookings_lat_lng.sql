-- ─────────────────────────────────────────────────────────────────────────────
-- 0001 — add explicit latitude / longitude columns to public.bookings.
--
-- Why
-- ----
-- The Android app's BookingDto serialises a customer-picked pin as plain
-- `latitude` and `longitude` keys. The current schema only holds a PostGIS
-- `coordinates` column, so as soon as the client sends real numbers Postgrest
-- raises:
--     "Could not find the 'latitude' column of 'bookings' in the schema cache"
--
-- This migration is fully additive: existing rows are untouched, the PostGIS
-- `coordinates` column is left in place for downstream geo queries, and we keep
-- the two values in sync via a trigger so either client can read either form.
--
-- Apply once via the Supabase SQL editor (or `supabase db push` if you use the
-- CLI). Safe to re-run thanks to the IF NOT EXISTS / OR REPLACE guards.
-- ─────────────────────────────────────────────────────────────────────────────

ALTER TABLE public.bookings
    ADD COLUMN IF NOT EXISTS latitude  double precision,
    ADD COLUMN IF NOT EXISTS longitude double precision;

-- Backfill from the existing PostGIS column for any rows that already had it.
UPDATE public.bookings
SET latitude  = ST_Y(coordinates::geometry),
    longitude = ST_X(coordinates::geometry)
WHERE coordinates IS NOT NULL
  AND (latitude IS NULL OR longitude IS NULL);

-- Keep `coordinates` and the (lat, lng) pair in sync — whichever side is set on
-- write wins. We use BEFORE INSERT/UPDATE so the row hits disk consistent.
CREATE OR REPLACE FUNCTION public.bookings_sync_coordinates()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    -- Pair updated → mirror it into PostGIS `coordinates`.
    IF NEW.latitude IS NOT NULL AND NEW.longitude IS NOT NULL
       AND (TG_OP = 'INSERT'
            OR NEW.latitude IS DISTINCT FROM OLD.latitude
            OR NEW.longitude IS DISTINCT FROM OLD.longitude) THEN
        NEW.coordinates := ST_SetSRID(
            ST_MakePoint(NEW.longitude, NEW.latitude),
            4326
        )::geography;
    -- Only `coordinates` updated → derive the pair from it.
    ELSIF NEW.coordinates IS NOT NULL
          AND NEW.latitude IS NULL
          AND NEW.longitude IS NULL THEN
        NEW.latitude  := ST_Y(NEW.coordinates::geometry);
        NEW.longitude := ST_X(NEW.coordinates::geometry);
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS bookings_sync_coordinates ON public.bookings;
CREATE TRIGGER bookings_sync_coordinates
    BEFORE INSERT OR UPDATE
    ON public.bookings
    FOR EACH ROW
    EXECUTE FUNCTION public.bookings_sync_coordinates();

-- Force Postgrest to refresh its schema cache so the new columns become visible
-- without restarting the API container.
NOTIFY pgrst, 'reload schema';
