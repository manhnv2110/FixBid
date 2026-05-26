package com.example.fixbid.data.remote.vnpay

import android.util.Log
import com.example.fixbid.BuildConfig
import java.net.InetAddress
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * VNPay payment URL generator v2.1.0
 *
 * Sandbox:
 * - URL: https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
 * - TmnCode & HashSecret từ VNPay sandbox dashboard
 *
 * Production:
 * - URL: https://pay.vnpay.vn/vpcpay.html
 *
 */
@Singleton
class VNPayService @Inject constructor() {

    companion object {
        private const val TAG = "VNPayService"
        const val VNP_PAY_URL = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html"
        const val VNP_RETURN_URL = "fixbid://vnpay-return"
        const val VNP_VERSION = "2.1.0"
        const val VNP_COMMAND = "pay"
        const val VNP_CURRENCY_CODE = "VND"
        const val VNP_LOCALE = "vn"
        const val VNP_ORDER_TYPE = "other"
    }

    private val vnpTmnCode: String get() = BuildConfig.VNPAY_TMN_CODE.trim()
    private val vnpHashSecret: String get() = BuildConfig.VNPAY_HASH_SECRET.trim()

    /**
     * Tạo URL thanh toán VNPay.
     *
     * Quy trình:
     * 1. Sắp xếp các parameters alphabetically
     * 2. URL-encode tất cả values
     * 3. Tính hash trên chuỗi URL-encoded
     * 4. Thêm hash vào URL
     */
    fun createPaymentUrl(
        orderId: String,
        amount: Long,
        orderInfo: String,
        ipAddress: String = ""
    ): String {
        val finalIpAddress = if (ipAddress.isEmpty()) getClientIpAddress() else ipAddress

        val vnTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        val calendar = Calendar.getInstance(vnTimeZone)
        val formatter = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        formatter.timeZone = vnTimeZone
        val createDate = formatter.format(calendar.time)

        calendar.add(Calendar.MINUTE, 15)
        val expireDate = formatter.format(calendar.time)

        val params = linkedMapOf<String, String>()
        params["vnp_Amount"] = (amount * 100).toString()
        params["vnp_Command"] = VNP_COMMAND
        params["vnp_CreateDate"] = createDate
        params["vnp_CurrCode"] = VNP_CURRENCY_CODE
        params["vnp_ExpireDate"] = expireDate
        params["vnp_IpAddr"] = finalIpAddress
        params["vnp_Locale"] = VNP_LOCALE
        params["vnp_OrderInfo"] = orderInfo
        params["vnp_OrderType"] = VNP_ORDER_TYPE
        params["vnp_ReturnUrl"] = VNP_RETURN_URL
        params["vnp_TmnCode"] = vnpTmnCode
        params["vnp_TxnRef"] = orderId
        params["vnp_Version"] = VNP_VERSION

        // ====== DEBUG LOG ======
        Log.d(TAG, "══════════════════════════════════════════")
        Log.d(TAG, "VNPay Payment URL Generation Debug (v2.1.0):")
        Log.d(TAG, "TmnCode: '$vnpTmnCode'")
        Log.d(TAG, "HashSecret length: ${vnpHashSecret.length}")
        Log.d(TAG, "Amount: ${amount} VND → ${amount * 100} (x100)")
        Log.d(TAG, "IpAddress: $finalIpAddress")
        Log.d(TAG, "OrderInfo: $orderInfo")
        Log.d(TAG, "")
        Log.d(TAG, "Params (alphabetically sorted):")
        params.forEach { (k, v) -> Log.d(TAG, "  $k = $v") }
        Log.d(TAG, "")

        val encodedParams = params.mapValues { (_, value) ->
            URLEncoder.encode(value, "UTF-8")
        }

        Log.d(TAG, "Params (URL-encoded for hash):")
        encodedParams.forEach { (k, v) -> Log.d(TAG, "  $k = $v") }
        Log.d(TAG, "")

        val hashData = encodedParams.entries.joinToString("&") { (key, value) ->
            "$key=$value"
        }
        Log.d(TAG, "Hash Data (URL-encoded):")
        Log.d(TAG, hashData)
        Log.d(TAG, "")

        val secureHash = hmacSHA512(vnpHashSecret, hashData).uppercase()
        Log.d(TAG, "SecureHash (HmacSHA512, length=${secureHash.length}):")
        Log.d(TAG, secureHash)
        Log.d(TAG, "")

        val queryString = encodedParams.entries.joinToString("&") { (key, value) ->
            "$key=$value"
        }

        val fullUrl = "$VNP_PAY_URL?$queryString&vnp_SecureHash=$secureHash&vnp_SecureHashType=SHA512"
        Log.d(TAG, "Full Payment URL:")
        Log.d(TAG, fullUrl)
        Log.d(TAG, "══════════════════════════════════════════")
        Log.d(TAG, "")

        return fullUrl
    }

    /**
     * Xác thực response từ VNPay callback.
     */
    fun verifyReturnUrl(params: Map<String, String>): Boolean {
        val secureHash = params["vnp_SecureHash"] ?: return false

        // Loại bỏ các field không cần thiết
        val hashParams = params.toSortedMap().filter {
            it.key != "vnp_SecureHash" && it.key != "vnp_SecureHashType"
        }

        val encodedHashParams = hashParams.mapValues { (_, value) ->
            URLEncoder.encode(value, "UTF-8")
        }

        val hashData = encodedHashParams.entries.joinToString("&") { (key, value) ->
            "$key=$value"
        }

        val calculatedHash = hmacSHA512(vnpHashSecret, hashData).uppercase()

        Log.d(TAG, "Verify Return URL:")
        Log.d(TAG, "  Received hash:  $secureHash")
        Log.d(TAG, "  Calculated:     $calculatedHash")
        Log.d(TAG, "  Match: ${secureHash.equals(calculatedHash, ignoreCase = true)}")

        return secureHash.equals(calculatedHash, ignoreCase = true)
    }

    /**
     * Kiểm tra mã phản hồi từ VNPay.
     * "00" = Thanh toán thành công
     */
    fun isPaymentSuccess(responseCode: String?): Boolean {
        return responseCode == "00"
    }

    /**
     * Lấy IP address của client
     */
    private fun getClientIpAddress(): String {
        return try {
            val hostname = InetAddress.getLocalHost().hostName
            InetAddress.getAllByName(hostname).firstOrNull()?.hostAddress ?: "127.0.0.1"
        } catch (e: Exception) {
            Log.e(TAG, "Error getting client IP: ${e.message}")
            "127.0.0.1"
        }
    }

    /**
     * Tính HMAC-SHA512
     */
    private fun hmacSHA512(key: String, data: String): String {
        try {
            val mac = Mac.getInstance("HmacSHA512")
            val secretKeySpec = SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA512")
            mac.init(secretKeySpec)
            val hmacBytes = mac.doFinal(data.toByteArray(Charsets.UTF_8))

            // Chuyển đổi sang hex string (chữ hoa)
            return hmacBytes.joinToString("") { byte ->
                "%02x".format(byte)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calculating HMAC-SHA512: ${e.message}")
            return ""
        }
    }
}