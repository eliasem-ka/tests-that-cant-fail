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

        dispatch(CheckoutAction.CouponValidationStarted)
        val action = when (val result = repository.find(intent.code)) {
            is CouponResult.Found -> CheckoutAction.CouponApplied(result.coupon)
            CouponResult.NotFound, CouponResult.NetworkError -> CheckoutAction.CouponRejected(CouponError.NOT_FOUND)
        }
        dispatch(action)
    }
}
