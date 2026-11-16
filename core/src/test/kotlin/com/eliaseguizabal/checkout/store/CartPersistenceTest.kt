package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.CartStore
import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.data.InMemoryCartStore
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.domain.Quote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CartPersistenceTest {

    private val mug = CartItem(sku = "Mug", unitPriceCents = 3_000, quantity = 1)
    private val pen = CartItem(sku = "Pen", unitPriceCents = 250, quantity = 2)
    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)
    private val staleQuote = Quote(1, 2, 3, 4, 5, 6)

    /**
     * A store whose save caches a quote for what it just saved, as the [CartStore] contract describes
     * ("the quote cached from the last save"). Clearing must therefore happen before saving, not after.
     */
    private class CachingCartStore : CartStore {
        var saved: CartSnapshot? = null
        var cachedFor: CartSnapshot? = null

        override suspend fun clearCachedQuote() {
            cachedFor = null
        }

        override suspend fun save(snapshot: CartSnapshot) {
            saved = snapshot
            cachedFor = snapshot
        }
    }

    private fun store(scope: CoroutineScope, cartStore: CartStore, items: List<CartItem> = listOf(mug, pen)): CheckoutStore {
        val calculator = PriceCalculator()
        val reducer = CheckoutReducer(calculator)
        val repository = CouponRepository { code ->
            if (code == save10.code) CouponResult.Found(save10) else CouponResult.NotFound
        }
        return CheckoutStore(
            initial = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(items)),
            reducer = reducer,
            middlewares = listOf(
                CartMiddleware(),
                CouponMiddleware(repository, calculator),
                CartPersistenceMiddleware(cartStore),
            ),
            scope = scope,
        )
    }

    private fun TestScope.send(store: CheckoutStore, intent: CheckoutIntent) {
        store.dispatch(intent)
        runCurrent()
    }

    @Test
    fun `save stores the current items and no coupon code when none is applied`() = runTest {
        val cartStore = InMemoryCartStore()
        val store = store(backgroundScope, cartStore)

        send(store, CheckoutIntent.SaveCart)

        assertEquals(CartSnapshot(listOf(mug, pen), couponCode = null), cartStore.lastSnapshot)
    }

    @Test
    fun `save stores the cart as edited, with the applied coupon code`() = runTest {
        val cartStore = InMemoryCartStore()
        val store = store(backgroundScope, cartStore)
        send(store, CheckoutIntent.RemoveItem(pen.sku))
        send(store, CheckoutIntent.ApplyCoupon(save10.code))

        send(store, CheckoutIntent.SaveCart)

        assertEquals(CartSnapshot(listOf(mug), couponCode = "SAVE10"), cartStore.lastSnapshot)
    }

    @Test
    fun `save stores the coupon still applied, not a code that was just rejected`() = runTest {
        val cartStore = InMemoryCartStore()
        val store = store(backgroundScope, cartStore)
        send(store, CheckoutIntent.ApplyCoupon(save10.code))
        send(store, CheckoutIntent.ApplyCoupon("NOPE"))

        send(store, CheckoutIntent.SaveCart)

        assertEquals("SAVE10", cartStore.lastSnapshot?.couponCode)
    }

    @Test
    fun `save drops the stale cached quote`() = runTest {
        val cartStore = InMemoryCartStore().apply { cachedQuote = staleQuote }
        val store = store(backgroundScope, cartStore)

        send(store, CheckoutIntent.SaveCart)

        assertNull(cartStore.cachedQuote)
    }

    @Test
    fun `cache is cleared before saving, so the fresh save keeps its cache`() = runTest {
        val cartStore = CachingCartStore().apply { cachedFor = CartSnapshot(emptyList(), "OLD") }
        val store = store(backgroundScope, cartStore)

        send(store, CheckoutIntent.SaveCart)

        val expected = CartSnapshot(listOf(mug, pen), couponCode = null)
        assertEquals(expected, cartStore.saved)
        assertEquals(expected, cartStore.cachedFor)
    }

    @Test
    fun `other intents neither save nor touch the cache`() = runTest {
        val cartStore = InMemoryCartStore().apply { cachedQuote = staleQuote }
        val store = store(backgroundScope, cartStore)

        send(store, CheckoutIntent.AddItem(mug))
        send(store, CheckoutIntent.RemoveItem(pen.sku))
        send(store, CheckoutIntent.UsePoints(100))
        send(store, CheckoutIntent.ApplyCoupon(save10.code))

        assertNull(cartStore.lastSnapshot)
        assertEquals(staleQuote, cartStore.cachedQuote)
    }

    @Test
    fun `saving does not change the checkout state`() = runTest {
        val store = store(backgroundScope, InMemoryCartStore())
        send(store, CheckoutIntent.ApplyCoupon(save10.code))
        val before = store.state.value

        send(store, CheckoutIntent.SaveCart)

        assertEquals(before, store.state.value)
    }
}
