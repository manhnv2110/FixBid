package com.example.fixbid.testing

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Shared jqwik generators for FixBid property-based tests.
 *
 * Generators are written to constrain to *plausible* inputs:
 *   * monetary values respect the `numeric(15, 2)` precision in the schema
 *   * platform fee uses the production rate (10%) so `worker_receives` math
 *     mirrors the real RPC contract
 *   * IDs are real UUIDs so they round-trip through Postgres without coercion
 *
 * Add new generators here when reusing them across multiple PBT classes;
 * resist creating ad-hoc generators inline in tests.
 */
object Generators {

    /** Production platform fee rate as a fraction (10%). Mirrors PaymentConstants. */
    private const val PLATFORM_FEE_RATE: Double = 0.10

    /** Generator that produces a fresh, deterministic-per-iteration UUID. */
    fun arbitraryUuid(): Arbitrary<UUID> =
        Arbitraries.create { UUID.randomUUID() }

    /**
     * Fixture data describing a wallet row immediately after insert into
     * `public.wallets`. `total_earned` and `total_withdrawn` are 0 for
     * customer wallets and worker wallets in the pre-refund state of this
     * feature; refund itself doesn't touch them.
     */
    data class WalletFixture(
        val userId: UUID,
        val balance: BigDecimal,
        val pendingBalance: BigDecimal
    )

    /**
     * Generates a wallet snapshot with non-negative balances bounded to
     * realistic VND amounts (≤ 100M VND). Property tests use this for the
     * customer's pre-refund wallet (post-refund the balance grows by
     * `payment.amount`).
     */
    fun arbitraryWallet(userId: UUID): Arbitrary<WalletFixture> {
        val balance = Arbitraries.bigDecimals()
            .between(BigDecimal.ZERO, BigDecimal("100000000"))
            .ofScale(2)
            .map { it.setScale(2, RoundingMode.HALF_UP) }
        val pending = Arbitraries.bigDecimals()
            .between(BigDecimal.ZERO, BigDecimal("100000000"))
            .ofScale(2)
            .map { it.setScale(2, RoundingMode.HALF_UP) }
        return Combinators.combine(balance, pending).`as` { b, p ->
            WalletFixture(userId, b, p)
        }
    }

    /**
     * Fixture data describing a `payments` row in `escrow_status = 'holding'`.
     * Amounts use the production fee split: `worker_receives = amount - platform_fee`
     * with `platform_fee = round2(amount * 10%)`.
     */
    data class HoldingPaymentFixture(
        val id: UUID,
        val bookingId: UUID,
        val customerId: UUID,
        val workerId: UUID,
        val amount: BigDecimal,
        val platformFee: BigDecimal,
        val workerReceives: BigDecimal,
        val transactionId: String
    )

    /**
     * Generates a payment in HOLDING state for the given worker/customer pair.
     * `amount` ranges across realistic booking prices: 10k VND … 50M VND.
     * The fee split mirrors `PaymentConstants.platformFee()` (10%).
     */
    fun arbitraryPaymentInHolding(
        workerId: UUID,
        customerId: UUID
    ): Arbitrary<HoldingPaymentFixture> {
        val amount = Arbitraries.bigDecimals()
            .between(BigDecimal("10000"), BigDecimal("50000000"))
            .ofScale(2)
            .map { it.setScale(2, RoundingMode.HALF_UP) }
        return Combinators.combine(arbitraryUuid(), arbitraryUuid(), amount).`as` { id, bookingId, amt ->
            val fee = (amt * BigDecimal(PLATFORM_FEE_RATE)).setScale(2, RoundingMode.HALF_UP)
            val workerReceives = (amt - fee).setScale(2, RoundingMode.HALF_UP)
            HoldingPaymentFixture(
                id = id,
                bookingId = bookingId,
                customerId = customerId,
                workerId = workerId,
                amount = amt,
                platformFee = fee,
                workerReceives = workerReceives,
                transactionId = "FXB_TEST_${id.toString().take(8)}"
            )
        }
    }
}
