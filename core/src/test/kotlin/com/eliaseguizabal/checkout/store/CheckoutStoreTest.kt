@file:OptIn(ExperimentalCoroutinesApi::class)

package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartStore
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutStoreTest {

    private val calculator = PriceCalculator()
    private val fiveOff = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)

    private fun TestScope.storeWith(
        repo: FakeCouponRepository = FakeCouponRepository(),
        cartStore: CartStore = RecordingCartStore(),
        extra: List<Middleware> = emptyList(),
    ) = CheckoutStore(
        initial = CheckoutState(),
        reducer = CheckoutReducer(calculator),
        middlewares = listOf(
            CartMiddleware(),
            CouponMiddleware(repo, calculator),
            CartPersistenceMiddleware(cartStore),
        ) + extra,
        scope = this,
    )

    @Test
    fun `initial state is the one it was built with`() = runTest {
        val store = storeWith()
        assertEquals(CheckoutState(), store.state.value)
    }

    @Test
    fun `AddItem flows through the middleware into the state and quote`() = runTest {
        val store = storeWith()
        store.dispatch(CheckoutIntent.AddItem(CartItem("A", 1_000, 1)))
        advanceUntilIdle()

        assertEquals(listOf(CartItem("A", 1_000, 1)), store.state.value.items)
        assertEquals(1_729, store.state.value.quote.totalCents)
    }

    @Test
    fun `applying a coupon reaches the state with its discount in the quote`() = runTest {
        val repo = FakeCouponRepository(CouponResult.Found(fiveOff))
        val store = storeWith(repo = repo)

        store.dispatch(CheckoutIntent.AddItem(CartItem("A", 2_000, 1)))
        store.dispatch(CheckoutIntent.ApplyCoupon("fiveoff"))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(fiveOff, state.appliedCoupon)
        assertFalse(state.isValidatingCoupon)
        assertNull(state.couponError)
        assertEquals(500, state.quote.discountCents)
        // discounted 1500: tax 195, shipping 599
        assertEquals(2_294, state.quote.totalCents)
    }

    @Test
    fun `a rejected coupon surfaces its error and leaves the cart alone`() = runTest {
        val store = storeWith(repo = FakeCouponRepository(CouponResult.NotFound))

        store.dispatch(CheckoutIntent.AddItem(CartItem("A", 2_000, 1)))
        store.dispatch(CheckoutIntent.ApplyCoupon("NOPE"))
        advanceUntilIdle()

        val state = store.state.value
        assertEquals(CouponError.NOT_FOUND, state.couponError)
        assertNull(state.appliedCoupon)
        assertFalse(state.isValidatingCoupon)
        assertEquals(2_000, state.quote.subtotalCents)
    }

    @Test
    fun `a second coupon request while one is in flight is dropped`() = runTest {
        val repo = FakeCouponRepository(CouponResult.Found(fiveOff), latencyMs = 100)
        val store = storeWith(repo = repo)

        store.dispatch(CheckoutIntent.ApplyCoupon("FIVEOFF"))
        store.dispatch(CheckoutIntent.ApplyCoupon("FIVEOFF"))
        advanceUntilIdle()

        assertEquals(listOf("FIVEOFF"), repo.requestedCodes)
        assertEquals(fiveOff, store.state.value.appliedCoupon)
    }

    @Test
    fun `SaveCart persists the current cart and coupon code`() = runTest {
        val cartStore = RecordingCartStore()
        val store = storeWith(repo = FakeCouponRepository(CouponResult.Found(fiveOff)), cartStore = cartStore)

        store.dispatch(CheckoutIntent.AddItem(CartItem("A", 2_000, 1)))
        store.dispatch(CheckoutIntent.ApplyCoupon("FIVEOFF"))
        store.dispatch(CheckoutIntent.SaveCart)
        advanceUntilIdle()

        assertEquals(listOf("clearCachedQuote", "save"), cartStore.calls)
        assertEquals("FIVEOFF", cartStore.saved.single().couponCode)
        assertEquals(listOf(CartItem("A", 2_000, 1)), cartStore.saved.single().items)
    }

    @Test
    fun `middlewares run in order for each intent`() = runTest {
        val order = mutableListOf<String>()
        fun tagging(name: String) = Middleware { intent, _, _ -> order += "$name:${intent::class.simpleName}" }

        val store = storeWith(extra = listOf(tagging("first"), tagging("second")))
        store.dispatch(CheckoutIntent.UsePoints(10))
        advanceUntilIdle()

        assertEquals(listOf("first:UsePoints", "second:UsePoints"), order)
    }
}
