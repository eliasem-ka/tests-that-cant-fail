package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.Coupon

/** What happened. Reduced into a new [CheckoutState]. */
sealed interface CheckoutAction {
    data class ItemsChanged(val items: List<CartItem>) : CheckoutAction
    data class PointsChanged(val cents: Long) : CheckoutAction
    data object CouponValidationStarted : CheckoutAction
    data class CouponApplied(val coupon: Coupon) : CheckoutAction
    data class CouponRejected(val error: CouponError) : CheckoutAction
    data object CartSaved : CheckoutAction
}
