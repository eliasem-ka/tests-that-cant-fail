package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.data.InMemoryCartStore
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class CheckoutStoreTest {
    private val coupon = AmountOff("FIVEOFF", 0, 500)

    private fun TestScope.store(cart: InMemoryCartStore = InMemoryCartStore(), result: CouponResult = CouponResult.Found(coupon)): CheckoutStore {
        val calc = PriceCalculator()
        return CheckoutStore(
            CheckoutState(), CheckoutReducer(calc),
            listOf(CartMiddleware(), CouponMiddleware({ result }, calc), CartPersistenceMiddleware(cart)),
            TestScope(UnconfinedTestDispatcher(testScheduler)),
        )
    }

    @Test fun `adding items updates state and quote`() = runTest {
        val s = store()
        s.dispatch(CheckoutIntent.AddItem(CartItem("a", 1_000, 2)))
        assertEquals(2_000, s.state.value.quote.subtotalCents)
    }

    @Test fun `applying coupon end to end`() = runTest {
        val s = store()
        s.dispatch(CheckoutIntent.AddItem(CartItem("a", 1_000, 1)))
        s.dispatch(CheckoutIntent.ApplyCoupon("FIVEOFF"))
        assertEquals(coupon, s.state.value.appliedCoupon)
        assertEquals(500, s.state.value.quote.discountCents)
    }

    @Test fun `rejected coupon sets error`() = runTest {
        val s = store(result = CouponResult.NotFound)
        s.dispatch(CheckoutIntent.ApplyCoupon("nope"))
        assertEquals(CouponError.NOT_FOUND, s.state.value.couponError)
        assertNull(s.state.value.appliedCoupon)
    }

    @Test fun `save cart persists snapshot`() = runTest {
        val cart = InMemoryCartStore()
        val s = store(cart)
        s.dispatch(CheckoutIntent.AddItem(CartItem("a", 1_000, 1)))
        s.dispatch(CheckoutIntent.SaveCart)
        assertEquals(listOf(CartItem("a", 1_000, 1)), cart.lastSnapshot?.items)
    }
}
