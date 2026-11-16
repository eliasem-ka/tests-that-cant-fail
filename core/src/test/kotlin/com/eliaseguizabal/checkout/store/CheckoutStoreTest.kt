package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.InMemoryCartStore
import com.eliaseguizabal.checkout.data.InMemoryCouponRepository
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The store wired with the real reducer and middlewares, on virtual time. */
class CheckoutStoreTest {

    private val headphones = CartItem("headphones", 3_999, 1)
    private val cable = CartItem("cable", 899, 2)
    private val cartStore = InMemoryCartStore()

    private fun TestScope.store(): CheckoutStore {
        val calculator = PriceCalculator()
        return CheckoutStore(
            initial = CheckoutState(),
            reducer = CheckoutReducer(calculator),
            middlewares = listOf(
                CartMiddleware(),
                CouponMiddleware(InMemoryCouponRepository(latencyMs = 800), calculator),
                CartPersistenceMiddleware(cartStore),
            ),
            scope = this,
        )
    }

    @Test
    fun `dispatch does not change state until the scope runs`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(headphones))

        assertEquals(CheckoutState(), store.state.value)
        runCurrent()
        assertEquals(listOf(headphones), store.state.value.items)
    }

    @Test
    fun `adding and removing items keeps the quote in sync`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(headphones))
        store.dispatch(CheckoutIntent.AddItem(cable))
        store.dispatch(CheckoutIntent.AddItem(cable.copy(quantity = 1)))
        advanceUntilIdle()

        // 3999 + 3 * 899 = 6696; tax 870; free shipping
        assertEquals(listOf(headphones, cable.copy(quantity = 3)), store.state.value.items)
        assertEquals(6_696 + 870, store.state.value.quote.totalCents)

        store.dispatch(CheckoutIntent.RemoveItem("headphones"))
        advanceUntilIdle()

        // 2697; tax 351; shipping 599
        assertEquals(2_697 + 351 + 599, store.state.value.quote.totalCents)
    }

    @Test
    fun `applying a coupon shows validation in progress, then applies the discount`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(headphones)) // 3999
        runCurrent()

        store.dispatch(CheckoutIntent.ApplyCoupon("save10"))
        runCurrent()
        assertTrue(store.state.value.isValidatingCoupon)
        assertNull(store.state.value.appliedCoupon)

        advanceTimeBy800()
        val state = store.state.value
        assertFalse(state.isValidatingCoupon)
        assertEquals("SAVE10", state.appliedCoupon?.code)
        assertEquals(399, state.quote.discountCents)
    }

    @Test
    fun `a second ApplyCoupon while validating is ignored`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(headphones))
        store.dispatch(CheckoutIntent.ApplyCoupon("SAVE10"))
        runCurrent()
        store.dispatch(CheckoutIntent.ApplyCoupon("OFFLINE"))
        advanceUntilIdle()

        assertEquals("SAVE10", store.state.value.appliedCoupon?.code)
        assertNull(store.state.value.couponError)
    }

    @Test
    fun `coupon below the minimum is rejected and not applied`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(cable)) // 1798 < 2000
        store.dispatch(CheckoutIntent.ApplyCoupon("SAVE10"))
        advanceUntilIdle()

        assertEquals(CouponError.BELOW_MINIMUM, store.state.value.couponError)
        assertNull(store.state.value.appliedCoupon)
        assertEquals(0, store.state.value.quote.discountCents)
    }

    @Test
    fun `using points reduces the total`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(cable)) // 1798 + 234 + 599 = 2631
        store.dispatch(CheckoutIntent.UsePoints(631))
        advanceUntilIdle()

        assertEquals(2_000, store.state.value.quote.totalCents)
    }

    @Test
    fun `saving persists the current cart and coupon`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(headphones))
        store.dispatch(CheckoutIntent.ApplyCoupon("FIVEOFF"))
        advanceUntilIdle()
        store.dispatch(CheckoutIntent.SaveCart)
        advanceUntilIdle()

        assertEquals(CartSnapshot(listOf(headphones), "FIVEOFF"), cartStore.lastSnapshot)
    }

    @Test
    fun `every middleware sees every intent, in order, with the state left by the previous one`() = runTest {
        val seen = mutableListOf<String>()
        val store = CheckoutStore(
            initial = CheckoutState(),
            reducer = CheckoutReducer(PriceCalculator()),
            middlewares = listOf(
                CartMiddleware(),
                Middleware { intent, state, _ -> seen += "second:$intent:${state().items.size}" },
            ),
            scope = this,
        )

        store.dispatch(CheckoutIntent.AddItem(headphones))
        store.dispatch(CheckoutIntent.SaveCart)
        advanceUntilIdle()

        assertEquals(
            listOf("second:${CheckoutIntent.AddItem(headphones)}:1", "second:SaveCart:1"),
            seen,
        )
    }

    private fun TestScope.advanceTimeBy800() {
        testScheduler.advanceTimeBy(800)
        runCurrent()
    }
}
