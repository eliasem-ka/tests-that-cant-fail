package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.CartStore
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.confirmVerified
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CartPersistenceMiddlewareTest {

    private val cartStore = mockk<CartStore> {
        coEvery { clearCachedQuote() } just runs
        coEvery { save(any()) } just runs
    }
    private val middleware = CartPersistenceMiddleware(cartStore)
    private val items = listOf(CartItem("cable", 899, 2))

    @Test
    fun `SaveCart clears the cached quote, then saves items and coupon code, then reports CartSaved`() = runTest {
        val state = CheckoutState(items = items, appliedCoupon = AmountOff("FIVEOFF", 0, 500))
        val dispatched = mutableListOf<CheckoutAction>()

        middleware.handle(CheckoutIntent.SaveCart, { state }, dispatched::add)

        coVerifyOrder {
            cartStore.clearCachedQuote()
            cartStore.save(CartSnapshot(items, "FIVEOFF"))
        }
        assertEquals(listOf<CheckoutAction>(CheckoutAction.CartSaved), dispatched)
    }

    @Test
    fun `SaveCart without a coupon saves a null code`() = runTest {
        middleware.handle(CheckoutIntent.SaveCart, { CheckoutState(items = items) }, {})

        coVerify { cartStore.save(CartSnapshot(items, null)) }
    }

    @Test
    fun `other intents never touch the store`() = runTest {
        val dispatched = mutableListOf<CheckoutAction>()
        listOf(
            CheckoutIntent.AddItem(items.first()),
            CheckoutIntent.RemoveItem("cable"),
            CheckoutIntent.ApplyCoupon("SAVE10"),
            CheckoutIntent.UsePoints(1),
        ).forEach { middleware.handle(it, { CheckoutState(items = items) }, dispatched::add) }

        confirmVerified(cartStore)
        assertEquals(emptyList<CheckoutAction>(), dispatched)
    }
}
