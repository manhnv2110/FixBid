package com.example.fixbid.core.utils

/**
 * Platform-wide payment constants. Centralised here so the percentage isn't
 * silently re-declared in repository code, payment UI and notification copy
 * — change once and every screen / SQL write follows.
 */
object PaymentConstants {

    /** Share of every booking the platform retains. Worker receives `1 - PLATFORM_FEE_RATE`. */
    const val PLATFORM_FEE_RATE: Double = 0.10

    /** "10%" — pre-formatted label for UI use to keep parity with [PLATFORM_FEE_RATE]. */
    val PLATFORM_FEE_LABEL: String = "${(PLATFORM_FEE_RATE * 100).toInt()}%"

    /** Returns the platform fee component of a gross booking amount. */
    fun platformFee(amount: Double): Double = amount * PLATFORM_FEE_RATE

    /** Net amount the worker receives after the platform fee is deducted. */
    fun workerReceives(amount: Double): Double = amount - platformFee(amount)
}
