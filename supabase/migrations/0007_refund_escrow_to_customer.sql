-- ============================================================================
-- FixBid: refund escrow back to customer (worker-cancel flow)
-- ============================================================================
--
-- Adds the third escrow RPC alongside the existing
--   * fn_hold_escrow_to_wallet      (VNPay → worker.pending_balance)
--   * fn_release_escrow_to_wallet   (worker.pending_balance → worker.balance)
--   * fn_refund_escrow_to_customer  (worker.pending_balance → customer.balance)  ← NEW
--
-- Triggered when the worker cancels a CONFIRMED booking after the customer has
-- already paid (escrow_status = HOLDING). Atomically:
--
--   1. wallets[worker].pending_balance  -= payments.worker_receives  (clamped at 0)
--   2. wallets[customer].balance        += payments.amount
--   3. wallet_transactions row for worker   (type=escrow_refund, amount=worker_receives)
--   4. wallet_transactions row for customer (type=escrow_refund, amount=amount)
--   5. payments.status = 'refunded', payments.escrow_status = 'refunded'
--
-- The customer is refunded the FULL `payments.amount` (including platform_fee)
-- because the cancellation is the worker's fault.
--
-- Apply via Supabase Studio → SQL editor, or `supabase db push`.
-- ============================================================================

-- ─── Unique-index migration ────────────────────────────────────────────────
--
-- The original `wallet_tx_unique_per_event` index was on (payment_id, type)
-- which works for hold/release (one ledger row per event) but blocks refund:
-- refund must write TWO rows for the same payment_id + type='escrow_refund'
-- (one for the worker's wallet, one for the customer's wallet).
--
-- Replace it with a per-user variant. Existing escrow_hold / escrow_release
-- rows still satisfy the new index because they include user_id.

drop index if exists public.wallet_tx_unique_per_event;

create unique index if not exists wallet_tx_unique_per_event_and_user
    on public.wallet_transactions(payment_id, type, user_id)
    where payment_id is not null;

-- ─── RPC: refund escrow from worker pending → customer balance ─────────────
--
-- Idempotent on (payment_id, escrow_refund, worker_id): a second call returns
-- the worker wallet snapshot without touching anything. Returns the worker
-- wallet snapshot AFTER the refund — the caller (Kotlin layer) wires the
-- customer wallet update via realtime on the wallets table.

create or replace function public.fn_refund_escrow_to_customer(p_payment_id uuid)
returns public.wallets
language plpgsql
security definer
set search_path = public
as $$
declare
    v_payment         public.payments;
    v_worker_wallet   public.wallets;
    v_customer_wallet public.wallets;
    v_existing        public.wallet_transactions;
begin
    select * into v_payment from public.payments where id = p_payment_id;
    if v_payment.id is null then
        raise exception 'Payment % not found', p_payment_id;
    end if;
    if v_payment.escrow_status <> 'holding' then
        raise exception 'Payment % is not in HOLDING state', p_payment_id;
    end if;

    -- Make sure both sides have a wallet row. Customer almost certainly does
    -- not yet (no prior wallet feature for customers), so this often inserts.
    v_worker_wallet   := public.fn_ensure_wallet(v_payment.worker_id);
    v_customer_wallet := public.fn_ensure_wallet(v_payment.customer_id);

    -- Idempotent guard — if the worker side ledger row already exists for
    -- this payment, just return the worker snapshot and don't touch anything.
    select * into v_existing
    from public.wallet_transactions
    where payment_id = p_payment_id
      and type = 'escrow_refund'
      and user_id = v_payment.worker_id;
    if v_existing.id is not null then
        return v_worker_wallet;
    end if;

    -- 1. Worker: pending -= worker_receives (clamp to 0 defensively).
    update public.wallets
    set pending_balance = greatest(pending_balance - v_payment.worker_receives, 0)
    where id = v_worker_wallet.id
    returning * into v_worker_wallet;

    -- 2. Customer: balance += amount (FULL amount including platform_fee).
    update public.wallets
    set balance = balance + v_payment.amount
    where id = v_customer_wallet.id
    returning * into v_customer_wallet;

    -- 3. Ledger row for worker — snapshots reflect POST-update worker wallet.
    insert into public.wallet_transactions(
        wallet_id, user_id, type, amount,
        balance_after, pending_balance_after,
        booking_id, payment_id, description, reference
    ) values (
        v_worker_wallet.id, v_worker_wallet.user_id, 'escrow_refund',
        v_payment.worker_receives,
        v_worker_wallet.balance, v_worker_wallet.pending_balance,
        v_payment.booking_id, v_payment.id,
        'Đơn hủy bởi thợ — đã hoàn tiền cho khách',
        v_payment.transaction_id
    );

    -- 4. Ledger row for customer — snapshots reflect POST-update customer wallet.
    insert into public.wallet_transactions(
        wallet_id, user_id, type, amount,
        balance_after, pending_balance_after,
        booking_id, payment_id, description, reference
    ) values (
        v_customer_wallet.id, v_customer_wallet.user_id, 'escrow_refund',
        v_payment.amount,
        v_customer_wallet.balance, v_customer_wallet.pending_balance,
        v_payment.booking_id, v_payment.id,
        'Hoàn tiền do thợ hủy đơn',
        v_payment.transaction_id
    );

    -- 5. Mark payment as refunded.
    update public.payments
    set status = 'refunded',
        escrow_status = 'refunded'
    where id = v_payment.id;

    return v_worker_wallet;
end;
$$;

grant execute on function public.fn_refund_escrow_to_customer(uuid) to authenticated;
