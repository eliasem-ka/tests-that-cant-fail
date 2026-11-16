package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.CartItem
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CartMiddlewareTest {

    private val middleware = CartMiddleware()
    private val headphones = CartItem("headphones", 3_999, 1)
    private val cable = CartItem("cable", 899, 2)

    private suspend fun run(intent: CheckoutIntent, state: CheckoutState): List<CheckoutAction> {
        val dispatched = mutableListOf<CheckoutAction>()
        middleware.handle(intent, { state }, dispatched::add)
        return dispatched
    }

    @Test
    fun `adding a new sku appends it`() = runTest {
        val actions = run(CheckoutIntent.AddItem(cable), CheckoutState(items = listOf(headphones)))

        assertEquals(listOf(CheckoutAction.ItemsChanged(listOf(headphones, cable))), actions)
    }

    @Test
    fun `adding an existing sku merges quantities and keeps its position`() = runTest {
        val state = CheckoutState(items = listOf(headphones, cable))
        val actions = run(CheckoutIntent.AddItem(headphones.copy(quantity = 3)), state)

        assertEquals(
            listOf(CheckoutAction.ItemsChanged(listOf(headphones.copy(quantity = 4), cable))),
            actions,
        )
    }

    @Test
    fun `removing a sku drops every line with it`() = runTest {
        val actions = run(CheckoutIntent.RemoveItem("headphones"), CheckoutState(items = listOf(headphones, cable)))

        assertEquals(listOf(CheckoutAction.ItemsChanged(listOf(cable))), actions)
    }

    @Test
    fun `removing a sku that is not in the cart dispatches nothing`() = runTest {
        val actions = run(CheckoutIntent.RemoveItem("missing"), CheckoutState(items = listOf(headphones)))

        assertEquals(emptyList<CheckoutAction>(), actions)
    }

    @Test
    fun `using points dispatches PointsChanged with the amount`() = runTest {
        assertEquals(
            listOf(CheckoutAction.PointsChanged(250)),
            run(CheckoutIntent.UsePoints(250), CheckoutState()),
        )
    }

    @Test
    fun `ignores coupon and save intents`() = runTest {
        assertEquals(emptyList<CheckoutAction>(), run(CheckoutIntent.ApplyCoupon("SAVE10"), CheckoutState()))
        assertEquals(emptyList<CheckoutAction>(), run(CheckoutIntent.SaveCart, CheckoutState()))
    }
}
