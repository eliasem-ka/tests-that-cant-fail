package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.PriceCalculator

class CouponMiddleware(
    private val repository: CouponRepository,
    private val calculator: PriceCalculator,
) : Middleware {

    override suspend fun handle(
        intent: CheckoutIntent,
        state: () -> CheckoutState,
        dispatch: (CheckoutAction) -> Unit,
    ) {
        if (intent !is CheckoutIntent.ApplyCoupon) return
        if (state().isValidatingCoupon) return

        dispatch(CheckoutAction.CouponValidationStarted)
        val action = when (val result = repository.find(intent.code)) {
            is CouponResult.Found ->
                if (calculator.isEligible(result.coupon, state().quote.subtotalCents)) {
                    CheckoutAction.CouponApplied(result.coupon)
                } else {
                    CheckoutAction.CouponRejected(CouponError.BELOW_MINIMUM)
                }
            CouponResult.NotFound -> CheckoutAction.CouponRejected(CouponError.NOT_FOUND)
            CouponResult.NetworkError -> CheckoutAction.CouponRejected(CouponError.NETWORK)
        }
        dispatch(action)
    }
}
