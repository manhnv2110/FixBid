-- ============================================================================
-- FixBid wallet & ledger
-- ============================================================================
--
-- Two tables:
--   * wallets              — one row per worker, holds running balances.
--   * wallet_transactions  — append-only ledger. Every change to a wallet
--                            balance MUST come with a matching ledger row.
--
-- Two RPC functions provide atomic state changes so the in-app flow doesn't
-- have to juggle two writes (and risk drift if one fails):
--   * fn_hold_escrow_to_wallet(payment_id)     — VNPay return success
--   * fn_release_escrow_to_wallet(payment_id)  — customer confirmed completion
--
-- Apply this migration via Supabase Studio → SQL editor, or `supabase db push`.
-- ============================================================================

-- ─── Tables ────────────────────────────────────────────────────────────────

create table if not exists public.wallets (
    id                uuid primary key default gen_random_uuid(),
    user_id           uuid not null unique references auth.users(id) on delete cascade,
    balance           numeric(15, 2) not null default 0  check (balance >= 0),
    pending_balance   numeric(15, 2) not null default 0  check (pending_balance >= 0),
    total_earned      numeric(15, 2) not null default 0  check (total_earned >= 0),
    total_withdrawn   numeric(15, 2) not null default 0  check (total_withdrawn >= 0),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now()
);

create index if not exists wallets_user_id_idx on public.wallets(user_id);

create table if not exists public.wallet_transactions (
    id                     uuid primary key default gen_random_uuid(),
    wallet_id              uuid not null references public.wallets(id) on delete cascade,
    user_id                uuid not null,
    -- 'escrow_hold'    : tiền vào pending khi khách thanh toán xong (escrow holding)
    -- 'escrow_release' : pending → balance khi khách xác nhận hoàn thành
    -- 'escrow_refund'  : pending → 0 khi tranh chấp / hoàn tiền
    -- 'withdrawal'     : balance → 0 khi thợ rút tiền
    -- 'adjustment'     : ops chỉnh tay (giảm hoặc tăng)
    type                   text not null check (type in (
        'escrow_hold', 'escrow_release', 'escrow_refund',
        'withdrawal', 'adjustment'
    )),
    -- Giá trị tuyệt đối của giao dịch. Hướng vào/ra suy ra từ `type`.
    amount                 numeric(15, 2) not null check (amount > 0),
    balance_after          numeric(15, 2) not null check (balance_after >= 0),
    pending_balance_after  numeric(15, 2) not null check (pending_balance_after >= 0),
    booking_id             uuid references public.bookings(id) on delete set null,
    payment_id             uuid references public.payments(id) on delete set null,
    description            text,
    reference              text,
    created_at             timestamptz not null default now()
);

create index if not exists wallet_tx_wallet_id_idx     on public.wallet_transactions(wallet_id);
create index if not exists wallet_tx_user_id_idx       on public.wallet_transactions(user_id);
create index if not exists wallet_tx_booking_id_idx    on public.wallet_transactions(booking_id);
create index if not exists wallet_tx_payment_id_idx    on public.wallet_transactions(payment_id);
create index if not exists wallet_tx_created_at_idx    on public.wallet_transactions(created_at desc);

-- Một payment chỉ được hold / release / refund một lần. Nếu RPC bị gọi
-- hai lần (ví dụ retry recovery), insert thứ hai sẽ vi phạm unique và RPC
-- trả về dòng đã tồn tại → idempotent.
create unique index if not exists wallet_tx_unique_per_event
    on public.wallet_transactions(payment_id, type)
    where payment_id is not null;

-- ─── Updated-at trigger for wallets ────────────────────────────────────────

create or replace function public.fn_touch_wallet_updated_at()
returns trigger
language plpgsql
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

drop trigger if exists touch_wallet_updated_at on public.wallets;
create trigger touch_wallet_updated_at
    before update on public.wallets
    for each row execute function public.fn_touch_wallet_updated_at();

-- ─── Helper: ensure a wallet row exists for a worker ──────────────────────

create or replace function public.fn_ensure_wallet(p_user_id uuid)
returns public.wallets
language plpgsql
security definer
set search_path = public
as $$
declare
    v_wallet public.wallets;
begin
    select * into v_wallet from public.wallets where user_id = p_user_id;
    if v_wallet.id is null then
        insert into public.wallets(user_id) values (p_user_id)
        on conflict (user_id) do update set updated_at = now()
        returning * into v_wallet;
    end if;
    return v_wallet;
end;
$$;

-- ─── RPC: hold escrow into worker wallet's pending bucket ─────────────────
--
-- Called from the app right after VNPay marks a payment as escrow/holding.
-- Money does NOT move here from the platform's perspective — we just bump
-- the pending_balance so the worker can see "Đang giữ trong hệ thống".
-- Returns the wallet snapshot AFTER the change.

create or replace function public.fn_hold_escrow_to_wallet(p_payment_id uuid)
returns public.wallets
language plpgsql
security definer
set search_path = public
as $$
declare
    v_payment       public.payments;
    v_wallet        public.wallets;
    v_existing      public.wallet_transactions;
begin
    select * into v_payment from public.payments where id = p_payment_id;
    if v_payment.id is null then
        raise exception 'Payment % not found', p_payment_id;
    end if;
    if v_payment.worker_id is null then
        raise exception 'Payment % has no worker', p_payment_id;
    end if;

    -- Make sure the worker has a wallet row.
    v_wallet := public.fn_ensure_wallet(v_payment.worker_id);

    -- Idempotent: nếu đã có 'escrow_hold' cho payment này thì chỉ trả lại
    -- snapshot hiện tại, không cộng pending hai lần.
    select * into v_existing
    from public.wallet_transactions
    where payment_id = p_payment_id and type = 'escrow_hold';

    if v_existing.id is null then
        update public.wallets
        set pending_balance = pending_balance + v_payment.worker_receives
        where id = v_wallet.id
        returning * into v_wallet;

        insert into public.wallet_transactions(
            wallet_id, user_id, type, amount,
            balance_after, pending_balance_after,
            booking_id, payment_id,
            description, reference
        )
        values (
            v_wallet.id, v_wallet.user_id, 'escrow_hold', v_payment.worker_receives,
            v_wallet.balance, v_wallet.pending_balance,
            v_payment.booking_id, v_payment.id,
            'Khách đã thanh toán, đang chờ hoàn thành công việc',
            v_payment.transaction_id
        );
    end if;

    return v_wallet;
end;
$$;

-- ─── RPC: release escrow into worker wallet's available balance ───────────
--
-- Called when the customer confirms the work is done. Moves
-- worker_receives from pending → balance, bumps total_earned, and writes
-- one ledger row of type 'escrow_release'. Idempotent on repeated calls.

create or replace function public.fn_release_escrow_to_wallet(p_payment_id uuid)
returns public.wallets
language plpgsql
security definer
set search_path = public
as $$
declare
    v_payment       public.payments;
    v_wallet        public.wallets;
    v_existing      public.wallet_transactions;
    v_amount        numeric(15, 2);
begin
    select * into v_payment from public.payments where id = p_payment_id;
    if v_payment.id is null then
        raise exception 'Payment % not found', p_payment_id;
    end if;
    if v_payment.worker_id is null then
        raise exception 'Payment % has no worker', p_payment_id;
    end if;

    v_wallet := public.fn_ensure_wallet(v_payment.worker_id);
    v_amount := v_payment.worker_receives;

    -- Idempotent: nếu đã có release cho payment này thì trả luôn snapshot.
    select * into v_existing
    from public.wallet_transactions
    where payment_id = p_payment_id and type = 'escrow_release';
    if v_existing.id is not null then
        return v_wallet;
    end if;

    -- Move pending → balance + cộng total_earned. Bảo vệ pending_balance
    -- không xuống âm: clamp về tối đa số đang giữ. Trong thực tế hold luôn
    -- chạy trước nên không cần clamp, nhưng làm để chống corruption.
    update public.wallets
    set
        pending_balance = greatest(pending_balance - v_amount, 0),
        balance         = balance + v_amount,
        total_earned    = total_earned + v_amount
    where id = v_wallet.id
    returning * into v_wallet;

    insert into public.wallet_transactions(
        wallet_id, user_id, type, amount,
        balance_after, pending_balance_after,
        booking_id, payment_id,
        description, reference
    )
    values (
        v_wallet.id, v_wallet.user_id, 'escrow_release', v_amount,
        v_wallet.balance, v_wallet.pending_balance,
        v_payment.booking_id, v_payment.id,
        'Khách xác nhận hoàn thành — chuyển vào ví',
        coalesce(v_payment.transaction_id, 'FXB_' || substr(p_payment_id::text, 1, 8))
    );

    return v_wallet;
end;
$$;

-- ─── RLS ───────────────────────────────────────────────────────────────────

alter table public.wallets enable row level security;
alter table public.wallet_transactions enable row level security;

-- Worker can read their own wallet.
drop policy if exists "wallets_self_read" on public.wallets;
create policy "wallets_self_read" on public.wallets
    for select using (user_id = auth.uid());

-- All writes go through SECURITY DEFINER RPCs; nobody touches the table
-- directly. We still keep an explicit deny so misconfiguration of RPC
-- bypasses surface as a clear 401 instead of silent inserts.
drop policy if exists "wallets_no_direct_write" on public.wallets;
create policy "wallets_no_direct_write" on public.wallets
    for all using (false) with check (false);

drop policy if exists "wallet_tx_self_read" on public.wallet_transactions;
create policy "wallet_tx_self_read" on public.wallet_transactions
    for select using (user_id = auth.uid());

drop policy if exists "wallet_tx_no_direct_write" on public.wallet_transactions;
create policy "wallet_tx_no_direct_write" on public.wallet_transactions
    for all using (false) with check (false);

-- Allow authenticated users to invoke the RPCs (the functions themselves
-- run as SECURITY DEFINER and only act on the matching worker row).
grant execute on function public.fn_hold_escrow_to_wallet(uuid)    to authenticated;
grant execute on function public.fn_release_escrow_to_wallet(uuid) to authenticated;
grant execute on function public.fn_ensure_wallet(uuid)            to authenticated;

-- ============================================================================
-- Optional one-shot backfill: seed wallets + ledger from existing payment data
-- ============================================================================
-- Uncomment and run once if you already have completed payments before this
-- migration shipped. Idempotent — re-running won't double-credit because the
-- RPCs are guarded by the unique index on (payment_id, type).

-- do $$
-- declare
--     r record;
-- begin
--     for r in
--         select id from public.payments
--         where worker_id is not null
--         order by created_at asc
--     loop
--         -- Recreate the hold whenever the payment ever entered escrow.
--         if exists (
--             select 1 from public.payments
--             where id = r.id
--               and (status in ('escrow', 'completed') or escrow_status in ('holding', 'released'))
--         ) then
--             perform public.fn_hold_escrow_to_wallet(r.id);
--         end if;
--
--         -- Then release if the payment is fully completed.
--         if exists (
--             select 1 from public.payments
--             where id = r.id
--               and (status = 'completed' or escrow_status = 'released')
--         ) then
--             perform public.fn_release_escrow_to_wallet(r.id);
--         end if;
--     end loop;
-- end $$;
