-- ============================================================================
-- Test bootstrap: minimal stub schema for FixBid PBT under Postgres Testcontainers
-- ============================================================================
--
-- The production schema lives across many places (Supabase auth + numerous
-- migrations not all present in `supabase/migrations/`). For unit-level
-- property-based tests against `fn_refund_escrow_to_customer`, we only need
-- the surface that `20260603_wallets.sql` and `0007_refund_escrow_to_customer.sql`
-- touch:
--   * `auth.users(id)`           — referenced by `wallets.user_id` FK
--   * `public.bookings(id)`      — referenced by `wallet_transactions.booking_id` FK
--   * `public.payments(...)`     — read by the refund RPC
--
-- These stubs include only the columns the migrations / RPC actually read or
-- write. Other production columns are intentionally omitted to keep the
-- testbed tight and the migrations applicable verbatim.
-- ============================================================================

create extension if not exists pgcrypto;

-- The Supabase migrations grant execute on the RPCs to the `authenticated`
-- role and reference `auth.uid()` from RLS policies. Stub both so the
-- migrations apply cleanly under a vanilla Postgres testcontainer.
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated;
    end if;
end $$;

create schema if not exists auth;

create or replace function auth.uid() returns uuid
language sql stable as $$ select null::uuid $$;

create table if not exists auth.users (
    id uuid primary key default gen_random_uuid()
);

create table if not exists public.bookings (
    id          uuid primary key default gen_random_uuid(),
    customer_id uuid,
    worker_id   uuid,
    status      text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

create table if not exists public.payments (
    id              uuid primary key default gen_random_uuid(),
    booking_id      uuid references public.bookings(id) on delete set null,
    customer_id     uuid not null,
    worker_id       uuid not null,
    amount          numeric(15, 2) not null,
    platform_fee    numeric(15, 2) not null default 0,
    worker_receives numeric(15, 2) not null,
    method          text not null default 'vnpay',
    status          text not null default 'pending',
    transaction_id  text,
    paid_at         timestamptz,
    released_at     timestamptz,
    escrow_status   text not null default 'none',
    created_at      timestamptz not null default now()
);
