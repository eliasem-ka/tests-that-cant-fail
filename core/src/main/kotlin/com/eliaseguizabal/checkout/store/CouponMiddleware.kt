package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.PriceCalculator
import java.util.concurrent.atomic.AtomicBoolean

class CouponMiddleware(
    private val repository: CouponRepository,
    private val calculator: PriceCalculator,
) : Middleware {

    /** Guards against a second Apply while a lookup is in flight, even on a multi-threaded dispatcher. */
    private val validating = AtomicBoolean(false)

    override suspend fun handle(
        intent: CheckoutIntent,
        state: () -> CheckoutState,
        dispatch: (CheckoutAction) -> Unit,
    ) {
        if (intent !is CheckoutIntent.ApplyCoupon) return
        if (!validating.compareAndSet(false, true)) return

        try {
            dispatch(CheckoutAction.CouponValidationStarted)
            val action = when (val result = repository.find(intent.code)) {
                is CouponResult.Found ->
                    if (calculator.isEligible(result.coupon, calculator.subtotal(state().items))) {
                        CheckoutAction.CouponApplied(result.coupon)
                    } else {
                        CheckoutAction.CouponRejected(CouponError.BELOW_MINIMUM)
                    }
                CouponResult.NotFound -> CheckoutAction.CouponRejected(CouponError.NOT_FOUND)
                CouponResult.NetworkError -> CheckoutAction.CouponRejected(CouponError.NETWORK)
            }
            dispatch(action)
        } finally {
            validating.set(false)
        }
    }
}
