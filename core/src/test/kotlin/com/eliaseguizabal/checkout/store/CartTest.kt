package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.domain.Quote
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** Cart editing through the real store: CartMiddleware + reducer + calculator. */
class CartTest {

    private val mug = CartItem(sku = "Mug", unitPriceCents = 1_000, quantity = 1)
    private val pen = CartItem(sku = "Pen", unitPriceCents = 250, quantity = 2)

    private fun store(scope: CoroutineScope, initialItems: List<CartItem> = emptyList()): CheckoutStore {
        val calculator = PriceCalculator()
        val reducer = CheckoutReducer(calculator)
        return CheckoutStore(
            initial = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(initialItems)),
            reducer = reducer,
            middlewares = listOf(CartMiddleware()),
            scope = scope,
        )
    }

    private fun TestScope.send(store: CheckoutStore, intent: CheckoutIntent) {
        store.dispatch(intent)
        runCurrent()
    }

    @Test
    fun `adding a new sku appends it and reprices`() = runTest {
        val store = store(backgroundScope, listOf(mug))

        send(store, CheckoutIntent.AddItem(pen))

        assertEquals(listOf(mug, pen), store.state.value.items)
        assertEquals(1_500, store.state.value.quote.subtotalCents)
    }

    @Test
    fun `adding a sku already in the cart merges the quantities in place`() = runTest {
        val store = store(backgroundScope, listOf(mug, pen))

        send(store, CheckoutIntent.AddItem(mug.copy(quantity = 3)))

        assertEquals(listOf(mug.copy(quantity = 4), pen), store.state.value.items)
        assertEquals(4_500, store.state.value.quote.subtotalCents)
    }

    @Test
    fun `removing a sku drops only that line and reprices`() = runTest {
        val store = store(backgroundScope, listOf(mug, pen))

        send(store, CheckoutIntent.RemoveItem(mug.sku))

        assertEquals(listOf(pen), store.state.value.items)
        assertEquals(500, store.state.value.quote.subtotalCents)
    }

    @Test
    fun `removing the last item leaves an empty, free cart`() = runTest {
        val store = store(backgroundScope, listOf(mug))

        send(store, CheckoutIntent.RemoveItem(mug.sku))

        assertEquals(emptyList<CartItem>(), store.state.value.items)
        assertEquals(Quote.EMPTY, store.state.value.quote)
    }

    @Test
    fun `removing a sku that is not in the cart changes nothing`() = runTest {
        val store = store(backgroundScope, listOf(mug, pen))
        val before = store.state.value

        send(store, CheckoutIntent.RemoveItem("Nope"))

        assertEquals(before, store.state.value)
    }

    @Test
    fun `using points records them and lowers the total`() = runTest {
        val store = store(backgroundScope, listOf(mug))
        // 10.00 + 1.30 tax + 5.99 shipping = 17.29
        assertEquals(1_729, store.state.value.quote.totalCents)

        send(store, CheckoutIntent.UsePoints(229))

        assertEquals(229, store.state.value.loyaltyPointsCents)
        assertEquals(229, store.state.value.quote.pointsAppliedCents)
        assertEquals(1_500, store.state.value.quote.totalCents)
    }

    @Test
    fun `each new add sees the cart left by the previous one`() = runTest {
        val store = store(backgroundScope)

        send(store, CheckoutIntent.AddItem(mug))
        send(store, CheckoutIntent.AddItem(mug))
        send(store, CheckoutIntent.AddItem(pen))

        assertEquals(listOf(mug.copy(quantity = 2), pen), store.state.value.items)
    }
}
