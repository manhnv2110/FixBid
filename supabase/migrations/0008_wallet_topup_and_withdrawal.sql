-- ============================================================================
-- FixBid: customer wallet top-up and withdrawal
-- ============================================================================
--
-- Adds two new wallet flows on top of the existing escrow plumbing:
--
--   * fn_credit_wallet_topup       — credit `balance` after a successful VNPay
--                                    top-up. Idempotent on `vnp_txn_ref`.
--   * fn_request_wallet_withdrawal — lock `balance` into `pending_balance`
--                                    when the user requests a payout. The
--                                    actual bank transfer happens off-app
--                                    (admin/ops); approval/reject flips the
--                                    lock back to balance or finalises it.
--
-- Two new tables track the side-effects:
--
--   * wallet_topups       — VNPay top-up attempts (links amount to vnp_TxnRef)
--   * wallet_withdrawals  — withdrawal requests (status: pending|processing|
--                           completed|rejected, bank account snapshot)
--
-- Two new ledger types extend the `wallet_transactions.type` CHECK list:
--
--   * topup               — balance += amount on a successful top-up.
--                           reference = vnp_txn_ref (idempotency key).
--   * withdrawal_request  — balance -= amount, pending_balance += amount when
--                           the user submits a withdrawal request. The
--                           pre-existing `withdrawal` type is reserved for
--                           the moment ops marks the request as completed.
--
-- Apply via Supabase Studio → SQL editor or `supabase db push`.
-- ============================================================================

-- ─── Extend wallet_transactions.type CHECK ─────────────────────────────────
--
-- Postgres requires us to drop the old constraint and recreate it with the
-- expanded enum-like list. Existing rows pass the new check trivially because
-- the old set is a subset.

alter table public.wallet_transactions
    drop constraint if exists wallet_transactions_type_check;

alter table public.wallet_transactions
    add constraint wallet_transactions_type_check
    check (type in (
        'escrow_hold', 'escrow_release', 'escrow_refund',
        'withdrawal', 'adjustment',
        'topup', 'withdrawal_request'
    ));

-- ─── Tables ───────────────────────────────────────────────────────────────

-- wallet_topups: one row per top-up attempt. Created in `pending` when the
-- customer submits the form, flipped to `completed` (or `failed`) by the
-- VNPay return handler.
create table if not exists public.wallet_topups (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null references auth.users(id) on delete cascade,
    amount          numeric(15, 2) not null check (amount > 0),
    vnp_txn_ref     text not null unique,
    transaction_id  text,                      -- vnp_TransactionNo, set on success
    status          text not null default 'pending'
                    check (status in ('pending', 'completed', 'failed', 'cancelled')),
    response_code   text,
    completed_at    timestamptz,
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index if not exists wallet_topups_user_id_idx
    on public.wallet_topups(user_id);
create index if not exists wallet_topups_status_idx
    on public.wallet_topups(status);
create index if not exists wallet_topups_created_at_idx
    on public.wallet_topups(created_at desc);

-- wallet_withdrawals: one row per withdrawal request. The actual bank
-- transfer is settled off-app; the table is the source of truth for
-- pending vs completed amounts.
create table if not exists public.wallet_withdrawals (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null references auth.users(id) on delete cascade,
    amount              numeric(15, 2) not null check (amount > 0),
    bank_name           text not null,
    bank_account_number text not null,
    bank_account_holder text not null,
    note                text,
    status              text not null default 'processing'
                        check (status in ('processing', 'completed', 'rejected', 'cancelled')),
    rejection_reason    text,
    completed_at        timestamptz,
    created_at          timestamptz not null default now(),
    updated_at          timestamptz not null default now()
);

create index if not exists wallet_withdrawals_user_id_idx
    on public.wallet_withdrawals(user_id);
create index if not exists wallet_withdrawals_status_idx
    on public.wallet_withdrawals(status);
create index if not exists wallet_withdrawals_created_at_idx
    on public.wallet_withdrawals(created_at desc);

-- ─── RPC: credit wallet top-up ─────────────────────────────────────────────
--
-- Called from the VNPay return handler when `vnp_TxnRef` starts with `TOPUP-`
-- and `vnp_ResponseCode = '00'`. We look the top-up row up by its
-- `vnp_txn_ref` (the value we shipped to VNPay and which the gateway echoes
-- back) rather than by `id` — there's no way for the client to learn the
-- generated row id between create and return, so vnp_txn_ref is the only
-- key both sides agree on.
--
-- Idempotent on (vnp_txn_ref): re-invoking with the same reference returns
-- the existing wallet snapshot without double-crediting.

create or replace function public.fn_credit_wallet_topup(
    p_vnp_txn_ref    text,
    p_transaction_id text
)
returns public.wallets
language plpgsql
security definer
set search_path = public
as $$
declare
    v_topup     public.wallet_topups;
    v_wallet    public.wallets;
    v_existing  public.wallet_transactions;
begin
    select * into v_topup from public.wallet_topups
    where vnp_txn_ref = p_vnp_txn_ref;

    if v_topup.id is null then
        raise exception 'Top-up % not found', p_vnp_txn_ref;
    end if;

    -- Already settled — return the wallet snapshot. Doesn't matter whether
    -- we got here via the return URL or a (future) IPN webhook.
    if v_topup.status = 'completed' then
        return public.fn_ensure_wallet(v_topup.user_id);
    end if;

    if v_topup.status <> 'pending' then
        raise exception 'Top-up % is not pending (status=%)',
            p_vnp_txn_ref, v_topup.status;
    end if;

    -- Defensive idempotency: if a ledger row keyed by vnp_txn_ref already
    -- exists, skip the credit but still flip the topup row to completed so
    -- the UI can move on.
    select * into v_existing
    from public.wallet_transactions
    where reference = v_topup.vnp_txn_ref
      and type = 'topup'
      and user_id = v_topup.user_id;

    v_wallet := public.fn_ensure_wallet(v_topup.user_id);

    if v_existing.id is null then
        update public.wallets
        set balance = balance + v_topup.amount
        where id = v_wallet.id
        returning * into v_wallet;

        insert into public.wallet_transactions(
            wallet_id, user_id, type, amount,
            balance_after, pending_balance_after,
            description, reference
        ) values (
            v_wallet.id, v_wallet.user_id, 'topup', v_topup.amount,
            v_wallet.balance, v_wallet.pending_balance,
            'Nạp tiền vào ví qua VNPay',
            v_topup.vnp_txn_ref
        );
    end if;

    update public.wallet_topups
    set status         = 'completed',
        transaction_id = p_transaction_id,
        response_code  = '00',
        completed_at   = now(),
        updated_at     = now()
    where id = v_topup.id;

    return v_wallet;
end;
$$;

-- Drop the old (broken) signature so leftover callers fail loudly instead of
-- silently calling the wrong overload.
drop function if exists public.fn_credit_wallet_topup(uuid, text);

grant execute on function public.fn_credit_wallet_topup(text, text) to authenticated;

-- ─── RPC: mark a top-up as failed ─────────────────────────────────────────

create or replace function public.fn_fail_wallet_topup(
    p_vnp_txn_ref   text,
    p_response_code text
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    update public.wallet_topups
    set status        = 'failed',
        response_code = p_response_code,
        updated_at    = now()
    where vnp_txn_ref = p_vnp_txn_ref and status = 'pending';
end;
$$;

drop function if exists public.fn_fail_wallet_topup(uuid, text);

grant execute on function public.fn_fail_wallet_topup(text, text) to authenticated;

-- ─── RPC: request a wallet withdrawal ─────────────────────────────────────
--
-- Locks `p_amount` from the caller's available balance into pending_balance
-- and creates a withdrawal_requests row in `processing`. The actual bank
-- transfer happens off-app; ops eventually flips the row to completed via
-- `fn_complete_wallet_withdrawal` or refunds it via `fn_reject_wallet_withdrawal`.

create or replace function public.fn_request_wallet_withdrawal(
    p_amount              numeric,
    p_bank_name           text,
    p_bank_account_number text,
    p_bank_account_holder text,
    p_note                text
)
returns public.wallet_withdrawals
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id      uuid := auth.uid();
    v_wallet       public.wallets;
    v_withdrawal   public.wallet_withdrawals;
begin
    if v_user_id is null then
        raise exception 'Not authenticated';
    end if;

    if p_amount is null or p_amount <= 0 then
        raise exception 'Amount must be greater than zero';
    end if;

    if length(coalesce(trim(p_bank_name), '')) = 0
       or length(coalesce(trim(p_bank_account_number), '')) = 0
       or length(coalesce(trim(p_bank_account_holder), '')) = 0 then
        raise exception 'Bank account details are required';
    end if;

    v_wallet := public.fn_ensure_wallet(v_user_id);

    if v_wallet.balance < p_amount then
        raise exception 'Số dư không đủ để rút (số dư: %)', v_wallet.balance;
    end if;

    -- Lock funds: balance -= amount, pending_balance += amount.
    update public.wallets
    set balance         = balance - p_amount,
        pending_balance = pending_balance + p_amount
    where id = v_wallet.id
    returning * into v_wallet;

    insert into public.wallet_withdrawals(
        user_id, amount, bank_name, bank_account_number, bank_account_holder, note
    ) values (
        v_user_id, p_amount, trim(p_bank_name), trim(p_bank_account_number),
        trim(p_bank_account_holder), nullif(trim(coalesce(p_note, '')), '')
    )
    returning * into v_withdrawal;

    insert into public.wallet_transactions(
        wallet_id, user_id, type, amount,
        balance_after, pending_balance_after,
        description, reference
    ) values (
        v_wallet.id, v_wallet.user_id, 'withdrawal_request', p_amount,
        v_wallet.balance, v_wallet.pending_balance,
        format('Yêu cầu rút %s vào TK %s — %s',
               p_amount::text, p_bank_account_number, p_bank_name),
        v_withdrawal.id::text
    );

    return v_withdrawal;
end;
$$;

grant execute on function public.fn_request_wallet_withdrawal(
    numeric, text, text, text, text
) to authenticated;

-- ─── Optional admin RPCs (for completeness; no UI yet) ────────────────────
--
-- These flip a withdrawal_request to its terminal state. Until an admin panel
-- exists, ops can call them from the SQL editor. They run as SECURITY DEFINER
-- so callers don't need direct table access. We defer the actual auth.uid()
-- check to a future admin-role policy.

create or replace function public.fn_complete_wallet_withdrawal(
    p_withdrawal_id uuid
)
returns public.wallets
language plpgsql
security definer
set search_path = public
as $$
declare
    v_wd      public.wallet_withdrawals;
    v_wallet  public.wallets;
begin
    select * into v_wd from public.wallet_withdrawals where id = p_withdrawal_id;
    if v_wd.id is null then
        raise exception 'Withdrawal % not found', p_withdrawal_id;
    end if;
    if v_wd.status <> 'processing' then
        raise exception 'Withdrawal % is not processing (status=%)',
            p_withdrawal_id, v_wd.status;
    end if;

    v_wallet := public.fn_ensure_wallet(v_wd.user_id);

    -- Drain the previously locked pending. Balance is unchanged because the
    -- amount left the system to the bank.
    update public.wallets
    set pending_balance = greatest(pending_balance - v_wd.amount, 0),
        total_withdrawn = total_withdrawn + v_wd.amount
    where id = v_wallet.id
    returning * into v_wallet;

    insert into public.wallet_transactions(
        wallet_id, user_id, type, amount,
        balance_after, pending_balance_after,
        description, reference
    ) values (
        v_wallet.id, v_wallet.user_id, 'withdrawal', v_wd.amount,
        v_wallet.balance, v_wallet.pending_balance,
        'Rút tiền thành công',
        v_wd.id::text
    );

    update public.wallet_withdrawals
    set status       = 'completed',
        completed_at = now(),
        updated_at   = now()
    where id = v_wd.id;

    return v_wallet;
end;
$$;

create or replace function public.fn_reject_wallet_withdrawal(
    p_withdrawal_id uuid,
    p_reason        text
)
returns public.wallets
language plpgsql
security definer
set search_path = public
as $$
declare
    v_wd      public.wallet_withdrawals;
    v_wallet  public.wallets;
begin
    select * into v_wd from public.wallet_withdrawals where id = p_withdrawal_id;
    if v_wd.id is null then
        raise exception 'Withdrawal % not found', p_withdrawal_id;
    end if;
    if v_wd.status <> 'processing' then
        raise exception 'Withdrawal % is not processing (status=%)',
            p_withdrawal_id, v_wd.status;
    end if;

    v_wallet := public.fn_ensure_wallet(v_wd.user_id);

    -- Unlock the funds: pending → balance.
    update public.wallets
    set pending_balance = greatest(pending_balance - v_wd.amount, 0),
        balance         = balance + v_wd.amount
    where id = v_wallet.id
    returning * into v_wallet;

    insert into public.wallet_transactions(
        wallet_id, user_id, type, amount,
        balance_after, pending_balance_after,
        description, reference
    ) values (
        v_wallet.id, v_wallet.user_id, 'adjustment', v_wd.amount,
        v_wallet.balance, v_wallet.pending_balance,
        format('Hoàn lại tiền do từ chối yêu cầu rút: %s',
               coalesce(p_reason, 'không có lý do')),
        v_wd.id::text
    );

    update public.wallet_withdrawals
    set status           = 'rejected',
        rejection_reason = p_reason,
        updated_at       = now()
    where id = v_wd.id;

    return v_wallet;
end;
$$;

grant execute on function public.fn_complete_wallet_withdrawal(uuid) to authenticated;
grant execute on function public.fn_reject_wallet_withdrawal(uuid, text) to authenticated;

-- ─── RLS ──────────────────────────────────────────────────────────────────

alter table public.wallet_topups enable row level security;
alter table public.wallet_withdrawals enable row level security;

drop policy if exists "wallet_topups_self_read" on public.wallet_topups;
create policy "wallet_topups_self_read" on public.wallet_topups
    as permissive for select using (user_id = auth.uid());

-- All writes go through SECURITY DEFINER RPCs to keep balance + ledger in
-- sync.
drop policy if exists "wallet_topups_no_direct_write" on public.wallet_topups;
create policy "wallet_topups_no_direct_write" on public.wallet_topups
    as restrictive for all using (false) with check (false);

drop policy if exists "wallet_withdrawals_self_read" on public.wallet_withdrawals;
create policy "wallet_withdrawals_self_read" on public.wallet_withdrawals
    as permissive for select using (user_id = auth.uid());

drop policy if exists "wallet_withdrawals_no_direct_write" on public.wallet_withdrawals;
create policy "wallet_withdrawals_no_direct_write" on public.wallet_withdrawals
    as restrictive for all using (false) with check (false);

-- ─── Insert RPC for top-up creation ───────────────────────────────────────
--
-- Callers (Kotlin layer) need to insert into wallet_topups, but RLS forbids
-- direct inserts. Provide a SECURITY DEFINER helper that returns the inserted
-- row so the app can then build the VNPay URL with vnp_TxnRef = TOPUP-<id>.

create or replace function public.fn_create_wallet_topup(
    p_amount      numeric,
    p_vnp_txn_ref text
)
returns public.wallet_topups
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
    v_topup   public.wallet_topups;
begin
    if v_user_id is null then
        raise exception 'Not authenticated';
    end if;
    if p_amount is null or p_amount <= 0 then
        raise exception 'Amount must be greater than zero';
    end if;
    if length(coalesce(trim(p_vnp_txn_ref), '')) = 0 then
        raise exception 'vnp_txn_ref is required';
    end if;

    insert into public.wallet_topups(user_id, amount, vnp_txn_ref)
    values (v_user_id, p_amount, p_vnp_txn_ref)
    returning * into v_topup;

    return v_topup;
end;
$$;

grant execute on function public.fn_create_wallet_topup(numeric, text) to authenticated;
