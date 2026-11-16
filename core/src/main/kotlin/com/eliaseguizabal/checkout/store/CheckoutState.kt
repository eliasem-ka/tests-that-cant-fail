package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.Coupon
import com.eliaseguizabal.checkout.domain.Quote

enum class CouponError { NOT_FOUND }

data class CheckoutState(
    val items: List<CartItem> = emptyList(),
    val appliedCoupon: Coupon? = null,
    val loyaltyPointsCents: Long = 0,
    val quote: Quote = Quote.EMPTY,
    val couponError: CouponError? = null,
    val isValidatingCoupon: Boolean = false,
)
