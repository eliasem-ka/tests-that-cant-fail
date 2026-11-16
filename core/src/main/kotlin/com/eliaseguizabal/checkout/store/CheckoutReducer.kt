package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.PriceCalculator

class CheckoutReducer(private val calculator: PriceCalculator) {

    fun reduce(state: CheckoutState, action: CheckoutAction): CheckoutState = when (action) {
        is CheckoutAction.ItemsChanged -> state.copy(items = action.items).requoted()
        is CheckoutAction.PointsChanged -> state.copy(loyaltyPointsCents = action.cents).requoted()
        CheckoutAction.CouponValidationStarted -> state.copy(isValidatingCoupon = true, couponError = null)
        is CheckoutAction.CouponApplied ->
            state.copy(appliedCoupon = action.coupon, isValidatingCoupon = false).requoted()
        is CheckoutAction.CouponRejected ->
            state.copy(couponError = action.error, isValidatingCoupon = false)
        CheckoutAction.CartSaved -> state
    }

    private fun CheckoutState.requoted(): CheckoutState =
        copy(quote = calculator.quote(items, appliedCoupon, loyaltyPointsCents))
}
