package com.eliaseguizabal.checkout.data

import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.PercentOff
import kotlinx.coroutines.delay

/** Stand-in for a coupon API. `OFFLINE` simulates a network failure. */
class InMemoryCouponRepository(private val latencyMs: Long = 800) : CouponRepository {

    private val coupons = listOf(
        PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500),
        PercentOff(code = "BIG20", minSubtotalCents = 10_000, percent = 20, maxDiscountCents = 2_500),
        AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500),
    ).associateBy { it.code }

    override suspend fun find(code: String): CouponResult {
        delay(latencyMs)
        val normalized = code.trim().uppercase()
        if (normalized == "OFFLINE") return CouponResult.NetworkError
        return coupons[normalized]?.let { CouponResult.Found(it) } ?: CouponResult.NotFound
    }
}
