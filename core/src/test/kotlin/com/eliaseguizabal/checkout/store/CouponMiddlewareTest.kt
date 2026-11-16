package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.domain.Quote
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CouponMiddlewareTest {

    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)
    private val lookups = mutableListOf<String>()

    private fun middleware(result: CouponResult) = CouponMiddleware(
        repository = { code -> lookups += code; result },
        calculator = PriceCalculator(),
    )

    private fun stateWithSubtotal(cents: Long) =
        CheckoutState(quote = Quote.EMPTY.copy(subtotalCents = cents, totalCents = cents))

    private suspend fun CouponMiddleware.run(intent: CheckoutIntent, state: CheckoutState): List<CheckoutAction> {
        val dispatched = mutableListOf<CheckoutAction>()
        handle(intent, { state }, dispatched::add)
        return dispatched
    }

    @Test
    fun `found and eligible coupon is applied`() = runTest {
        val actions = middleware(CouponResult.Found(save10))
            .run(CheckoutIntent.ApplyCoupon("save10"), stateWithSubtotal(2_000))

        assertEquals(listOf("save10"), lookups)
        assertEquals(
            listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponApplied(save10)),
            actions,
        )
    }

    @Test
    fun `found coupon below its minimum is rejected`() = runTest {
        val actions = middleware(CouponResult.Found(save10))
            .run(CheckoutIntent.ApplyCoupon("SAVE10"), stateWithSubtotal(1_999))

        assertEquals(
            listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponRejected(CouponError.BELOW_MINIMUM)),
            actions,
        )
    }

    @Test
    fun `unknown code is rejected as not found`() = runTest {
        val actions = middleware(CouponResult.NotFound)
            .run(CheckoutIntent.ApplyCoupon("NOPE"), stateWithSubtotal(5_000))

        assertEquals(
            listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponRejected(CouponError.NOT_FOUND)),
            actions,
        )
    }

    @Test
    fun `network failure is rejected as a network error`() = runTest {
        val actions = middleware(CouponResult.NetworkError)
            .run(CheckoutIntent.ApplyCoupon("SAVE10"), stateWithSubtotal(5_000))

        assertEquals(
            listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponRejected(CouponError.NETWORK)),
            actions,
        )
    }

    @Test
    fun `does nothing while another coupon is being validated`() = runTest {
        val actions = middleware(CouponResult.Found(save10))
            .run(CheckoutIntent.ApplyCoupon("SAVE10"), stateWithSubtotal(5_000).copy(isValidatingCoupon = true))

        assertEquals(emptyList<CheckoutAction>(), actions)
        assertEquals(emptyList<String>(), lookups)
    }

    @Test
    fun `ignores intents other than ApplyCoupon without calling the repository`() = runTest {
        val m = middleware(CouponResult.Found(save10))
        listOf(
            CheckoutIntent.SaveCart,
            CheckoutIntent.UsePoints(10),
            CheckoutIntent.RemoveItem("x"),
        ).forEach { assertEquals(emptyList<CheckoutAction>(), m.run(it, stateWithSubtotal(5_000))) }

        assertEquals(emptyList<String>(), lookups)
    }

    @Test
    fun `eligibility is checked against the cart as it is when the lookup returns`() = runTest {
        var state = stateWithSubtotal(5_000)
        val repository = CouponRepository {
            state = stateWithSubtotal(1_000) // the cart shrank while the request was in flight
            CouponResult.Found(save10)
        }
        val dispatched = mutableListOf<CheckoutAction>()

        CouponMiddleware(repository, PriceCalculator())
            .handle(CheckoutIntent.ApplyCoupon("SAVE10"), { state }, dispatched::add)

        assertEquals(CheckoutAction.CouponRejected(CouponError.BELOW_MINIMUM), dispatched.last())
    }
}
