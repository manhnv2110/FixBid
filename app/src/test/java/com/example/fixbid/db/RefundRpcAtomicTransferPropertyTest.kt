package com.example.fixbid.db

import com.example.fixbid.testing.Generators
import com.example.fixbid.testing.TestPostgres
import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.ForAll
import net.jqwik.api.From
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.Connection
import java.util.UUID

/**
 * Feature: worker-cancel-refund-flow,
 * Property 4: Refund RPC performs the atomic financial transfer.
 *
 * For any payment in `escrow_status = HOLDING` with prior worker wallet snapshot
 * `Wₚ` and prior customer wallet snapshot `Cₚ`, after
 * `fn_refund_escrow_to_customer(payment.id)` returns successfully:
 *   * `worker.pending_balance == max(Wₚ.pending_balance - payment.worker_receives, 0)`
 *   * `customer.balance == Cₚ.balance + payment.amount`
 *   * `payment.status == REFUNDED` AND `payment.escrow_status == REFUNDED`
 *   * Exactly two new `wallet_transactions` rows exist with `payment_id = payment.id`
 *     and `type = escrow_refund`: one with `user_id = worker_id, amount = worker_receives`
 *     and one with `user_id = customer_id, amount = payment.amount`.
 *
 * Validates: Requirements 3.2, 3.6, 3.7, 3.8.
 *
 * Runs against a real Postgres (Testcontainers) with the wallets +
 * `0007_refund_escrow_to_customer` migrations applied; the test seeds rows
 * directly via JDBC, invokes the RPC, and reads post-state back.
 */
class RefundRpcAtomicTransferPropertyTest {

    /**
     * One holding payment + arbitrary worker/customer wallet snapshots.
     * The worker UUID and customer UUID are baked into the seeded payment to
     * keep the FK graph consistent.
     */
    data class Scenario(
        val workerId: UUID,
        val customerId: UUID,
        val workerWallet: Generators.WalletFixture,
        val customerWallet: Generators.WalletFixture,
        val payment: Generators.HoldingPaymentFixture
    )

    @Provide
    fun scenarios(): Arbitrary<Scenario> {
        val workerId = Generators.arbitraryUuid()
        val customerId = Generators.arbitraryUuid()
        return workerId.flatMap { wId ->
            customerId.flatMap { cId ->
                val worker = Generators.arbitraryWallet(wId)
                val customer = Generators.arbitraryWallet(cId)
                val payment = Generators.arbitraryPaymentInHolding(wId, cId)
                net.jqwik.api.Combinators.combine(worker, customer, payment).`as` { wW, cW, p ->
                    Scenario(wId, cId, wW, cW, p)
                }
            }
        }.filter { it.workerId != it.customerId }
    }

    @Property(tries = 100)
    fun refundRpcPerformsAtomicFinancialTransfer(@ForAll @From("scenarios") s: Scenario) {
        TestPostgres.truncateAll()

        TestPostgres.connection().use { conn ->
            seed(conn, s)

            // Snapshot pre-state for the post-RPC math.
            val workerPendingPre = readWalletPending(conn, s.workerId)
            val customerBalancePre = readWalletBalance(conn, s.customerId)

            invokeRefundRpc(conn, s.payment.id)

            // ── Wallet post-state ────────────────────────────────────────
            val workerPendingPost = readWalletPending(conn, s.workerId)
            val customerBalancePost = readWalletBalance(conn, s.customerId)

            val expectedPending = (workerPendingPre - s.payment.workerReceives)
                .max(BigDecimal.ZERO)
                .setScale(2, RoundingMode.HALF_UP)
            assertEquals(
                0,
                workerPendingPost.compareTo(expectedPending),
                "worker.pending_balance mismatch: expected $expectedPending, got $workerPendingPost"
            )
            val expectedCustomerBalance = (customerBalancePre + s.payment.amount)
                .setScale(2, RoundingMode.HALF_UP)
            assertEquals(
                0,
                customerBalancePost.compareTo(expectedCustomerBalance),
                "customer.balance mismatch: expected $expectedCustomerBalance, got $customerBalancePost"
            )

            // ── Payment post-state ───────────────────────────────────────
            val (status, escrowStatus) = readPaymentStatuses(conn, s.payment.id)
            assertEquals("refunded", status, "payment.status should be REFUNDED")
            assertEquals("refunded", escrowStatus, "payment.escrow_status should be REFUNDED")

            // ── Ledger post-state ────────────────────────────────────────
            val ledgerRows = readEscrowRefundRows(conn, s.payment.id)
            assertEquals(
                2,
                ledgerRows.size,
                "Expected exactly 2 escrow_refund rows for payment ${s.payment.id}, got ${ledgerRows.size}"
            )
            val workerRow = ledgerRows.firstOrNull { it.userId == s.workerId }
            val customerRow = ledgerRows.firstOrNull { it.userId == s.customerId }
            assertNotNull(workerRow, "missing worker-side escrow_refund ledger row")
            assertNotNull(customerRow, "missing customer-side escrow_refund ledger row")
            assertEquals(
                0,
                workerRow!!.amount.compareTo(s.payment.workerReceives),
                "worker ledger.amount should equal payment.worker_receives"
            )
            assertEquals(
                0,
                customerRow!!.amount.compareTo(s.payment.amount),
                "customer ledger.amount should equal payment.amount"
            )
            assertTrue(workerRow.amount.signum() > 0, "wallet_transactions.amount must be positive (CHECK)")
            assertTrue(customerRow.amount.signum() > 0, "wallet_transactions.amount must be positive (CHECK)")
        }
    }

    // ── Helpers: schema seeding + readback ────────────────────────────────

    private fun seed(conn: Connection, s: Scenario) {
        // auth.users (worker + customer)
        conn.prepareStatement("insert into auth.users(id) values (?), (?)").use { ps ->
            ps.setObject(1, s.workerId)
            ps.setObject(2, s.customerId)
            ps.executeUpdate()
        }

        // public.bookings (one row, status irrelevant to the RPC)
        conn.prepareStatement(
            """
            insert into public.bookings(id, customer_id, worker_id, status)
            values (?, ?, ?, 'confirmed')
            """.trimIndent()
        ).use { ps ->
            ps.setObject(1, s.payment.bookingId)
            ps.setObject(2, s.customerId)
            ps.setObject(3, s.workerId)
            ps.executeUpdate()
        }

        // public.payments in HOLDING
        conn.prepareStatement(
            """
            insert into public.payments(
                id, booking_id, customer_id, worker_id,
                amount, platform_fee, worker_receives,
                method, status, escrow_status, transaction_id
            ) values (?, ?, ?, ?, ?, ?, ?, 'vnpay', 'escrow', 'holding', ?)
            """.trimIndent()
        ).use { ps ->
            ps.setObject(1, s.payment.id)
            ps.setObject(2, s.payment.bookingId)
            ps.setObject(3, s.customerId)
            ps.setObject(4, s.workerId)
            ps.setBigDecimal(5, s.payment.amount)
            ps.setBigDecimal(6, s.payment.platformFee)
            ps.setBigDecimal(7, s.payment.workerReceives)
            ps.setString(8, s.payment.transactionId)
            ps.executeUpdate()
        }

        // public.wallets (worker + customer with arbitrary pre-snapshots)
        conn.prepareStatement(
            """
            insert into public.wallets(user_id, balance, pending_balance)
            values (?, ?, ?), (?, ?, ?)
            """.trimIndent()
        ).use { ps ->
            ps.setObject(1, s.workerId); ps.setBigDecimal(2, s.workerWallet.balance); ps.setBigDecimal(3, s.workerWallet.pendingBalance)
            ps.setObject(4, s.customerId); ps.setBigDecimal(5, s.customerWallet.balance); ps.setBigDecimal(6, s.customerWallet.pendingBalance)
            ps.executeUpdate()
        }
    }

    private fun invokeRefundRpc(conn: Connection, paymentId: UUID) {
        conn.prepareStatement("select public.fn_refund_escrow_to_customer(?)").use { ps ->
            ps.setObject(1, paymentId)
            ps.executeQuery().use { rs ->
                assertTrue(rs.next(), "RPC must return a row")
            }
        }
    }

    private fun readWalletPending(conn: Connection, userId: UUID): BigDecimal =
        conn.prepareStatement("select pending_balance from public.wallets where user_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                check(rs.next()) { "no wallet row for $userId" }
                rs.getBigDecimal(1)
            }
        }

    private fun readWalletBalance(conn: Connection, userId: UUID): BigDecimal =
        conn.prepareStatement("select balance from public.wallets where user_id = ?").use { ps ->
            ps.setObject(1, userId)
            ps.executeQuery().use { rs ->
                check(rs.next()) { "no wallet row for $userId" }
                rs.getBigDecimal(1)
            }
        }

    private fun readPaymentStatuses(conn: Connection, paymentId: UUID): Pair<String, String> =
        conn.prepareStatement("select status, escrow_status from public.payments where id = ?").use { ps ->
            ps.setObject(1, paymentId)
            ps.executeQuery().use { rs ->
                check(rs.next()) { "no payment row for $paymentId" }
                rs.getString(1) to rs.getString(2)
            }
        }

    private data class LedgerRow(val userId: UUID, val amount: BigDecimal, val type: String)

    private fun readEscrowRefundRows(conn: Connection, paymentId: UUID): List<LedgerRow> =
        conn.prepareStatement(
            """
            select user_id, amount, type
            from public.wallet_transactions
            where payment_id = ? and type = 'escrow_refund'
            """.trimIndent()
        ).use { ps ->
            ps.setObject(1, paymentId)
            ps.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) {
                        add(
                            LedgerRow(
                                userId = rs.getObject(1) as UUID,
                                amount = rs.getBigDecimal(2),
                                type = rs.getString(3)
                            )
                        )
                    }
                }
            }
        }
}
