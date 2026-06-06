package com.example.fixbid.domain.usecase.customer

import com.example.fixbid.data.remote.vnpay.VNPayService
import com.example.fixbid.domain.model.Resource
import com.example.fixbid.domain.model.WalletTopup
import com.example.fixbid.domain.repository.WalletRepository
import javax.inject.Inject

/**
 * Initialise a wallet top-up flow:
 *
 *  1. Generate a unique `vnp_TxnRef` keyed by `TOPUP-<uuid>`. The prefix lets
 *     [ProcessVNPayReturnUseCase] disambiguate booking payments from top-ups
 *     when the return URL fires.
 *  2. Insert a `wallet_topups` row in PENDING via the repository (which goes
 *     through `fn_create_wallet_topup`, a SECURITY DEFINER helper).
 *  3. Build the VNPay payment URL and return both the topup snapshot and the
 *     URL so the caller can launch a browser intent.
 */
class CreateWalletTopupUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
    private val vnPayService: VNPayService
) {
    data class TopupResult(
        val topup: WalletTopup,
        val paymentUrl: String
    )

    companion object {
        const val TOPUP_TXN_REF_PREFIX = "TOPUP-"

        /** Min amount aligned with VNPay sandbox's documented floor (10,000 VND). */
        const val MIN_AMOUNT = 10_000.0

        /** Soft cap to keep top-ups within day-to-day in-app spending. */
        const val MAX_AMOUNT = 50_000_000.0
    }

    suspend operator fun invoke(amount: Double): Resource<TopupResult> {
        if (amount < MIN_AMOUNT) {
            return Resource.Error("Số tiền tối thiểu là ${MIN_AMOUNT.toLong()} đ")
        }
        if (amount > MAX_AMOUNT) {
            return Resource.Error("Số tiền tối đa mỗi lần là ${MAX_AMOUNT.toLong()} đ")
        }

        val txnRef = "$TOPUP_TXN_REF_PREFIX${java.util.UUID.randomUUID()}"

        val createResult = walletRepository.createTopup(amount, txnRef)
        val topup = when (createResult) {
            is Resource.Success -> createResult.data
            is Resource.Error -> return Resource.Error(createResult.message)
            Resource.Loading -> return Resource.Loading
        }

        val url = vnPayService.createPaymentUrl(
            // VNPay's TxnRef is the value the return URL will echo back; using
            // the prefixed ref (not the topup id) keeps the routing simple.
            orderId = topup.vnpTxnRef,
            amount = amount.toLong(),
            orderInfo = "Nap tien vao vi FixBid - ${topup.id.take(8)}"
        )

        return Resource.Success(TopupResult(topup = topup, paymentUrl = url))
    }
}
