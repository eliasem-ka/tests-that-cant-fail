package com.eliaseguizabal.checkout.oracle

import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CartMiddleware
import com.eliaseguizabal.checkout.store.CheckoutAction
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutReducer
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CheckoutStore
import com.eliaseguizabal.checkout.store.CouponError
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutStoreOracleTest {

    private val reducer = CheckoutReducer(PriceCalculator())

    private fun TestScope.store(initial: CheckoutState = CheckoutState()) =
        CheckoutStore(initial, reducer, listOf(CartMiddleware()), this)

    @Test
    fun `adding the same sku twice merges quantity and recomputes the quote`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(1500)))
        store.dispatch(CheckoutIntent.AddItem(item(1500)))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(listOf(item(1500, quantity = 2)), state.items)
        assertEquals(3000, state.quote.subtotalCents)
        assertEquals(3989, state.quote.totalCents)
    }

    @Test
    fun `removing a sku updates the quote`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(1000, sku = "A")))
        store.dispatch(CheckoutIntent.AddItem(item(2000, sku = "B")))
        store.dispatch(CheckoutIntent.RemoveItem("A"))
        advanceUntilIdle()

        assertEquals(listOf(item(2000, sku = "B")), store.state.value.items)
        assertEquals(2000, store.state.value.quote.subtotalCents)
    }

    @Test
    fun `removing a sku that is not in the cart leaves state unchanged`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(1000)))
        advanceUntilIdle()
        val before = store.state.value

        store.dispatch(CheckoutIntent.RemoveItem("missing"))
        advanceUntilIdle()

        assertEquals(before, store.state.value)
    }

    @Test
    fun `using points recomputes the total`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(1000)))
        store.dispatch(CheckoutIntent.UsePoints(500))
        advanceUntilIdle()

        assertEquals(500, store.state.value.quote.pointsAppliedCents)
        assertEquals(1229, store.state.value.quote.totalCents)
    }

    @Test
    fun `CouponRejected keeps the previously applied coupon and total`() {
        val applied = reducer.reduce(
            reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(listOf(item(2000)))),
            CheckoutAction.CouponApplied(SAVE10),
        )
        assertEquals(2633, applied.quote.totalCents)

        val validating = reducer.reduce(applied, CheckoutAction.CouponValidationStarted)
        val rejected = reducer.reduce(validating, CheckoutAction.CouponRejected(CouponError.NOT_FOUND))

        assertEquals(SAVE10, rejected.appliedCoupon)
        assertEquals(2633, rejected.quote.totalCents)
        assertEquals(CouponError.NOT_FOUND, rejected.couponError)
        assertFalse(rejected.isValidatingCoupon)
    }
}
