package com.eliaseguizabal.checkout.data

import com.eliaseguizabal.checkout.domain.Coupon

sealed interface CouponResult {
    data class Found(val coupon: Coupon) : CouponResult
    data object NotFound : CouponResult
    data object NetworkError : CouponResult
}

fun interface CouponRepository {
    suspend fun find(code: String): CouponResult
}
