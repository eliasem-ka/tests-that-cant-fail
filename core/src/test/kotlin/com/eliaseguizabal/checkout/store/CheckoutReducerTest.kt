package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.domain.Quote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutReducerTest {

    private val reducer = CheckoutReducer(PriceCalculator())

    private val cart = listOf(CartItem(sku = "A", unitPriceCents = 3_000, quantity = 1))
    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)

    private val withCart = CheckoutState(items = cart, quote = Quote(3_000, 0, 390, 599, 0, 3_989))

    @Test
    fun `items changed replaces the items and reprices them`() {
        val state = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart))

        assertEquals(cart, state.items)
        assertEquals(Quote(3_000, 0, 390, 599, 0, 3_989), state.quote)
    }

    @Test
    fun `items changed keeps the applied coupon and points in the new price`() {
        val start = CheckoutState(appliedCoupon = save10, loyaltyPointsCents = 50)

        val state = reducer.reduce(start, CheckoutAction.ItemsChanged(cart))

        assertEquals(Quote(3_000, 300, 351, 599, 50, 3_600), state.quote)
    }

    @Test
    fun `points changed records the points and reprices`() {
        val state = reducer.reduce(withCart, CheckoutAction.PointsChanged(989))

        assertEquals(989, state.loyaltyPointsCents)
        assertEquals(Quote(3_000, 0, 390, 599, 989, 3_000), state.quote)
    }

    @Test
    fun `validation started marks validating and clears the previous error but keeps the coupon`() {
        val start = withCart.copy(appliedCoupon = save10, couponError = CouponError.NOT_FOUND)

        val state = reducer.reduce(start, CheckoutAction.CouponValidationStarted)

        assertTrue(state.isValidatingCoupon)
        assertNull(state.couponError)
        assertEquals(save10, state.appliedCoupon)
        assertEquals(start.quote, state.quote)
    }

    @Test
    fun `coupon applied stores the coupon, stops validating and prices in the discount`() {
        val start = withCart.copy(isValidatingCoupon = true)

        val state = reducer.reduce(start, CheckoutAction.CouponApplied(save10))

        assertEquals(save10, state.appliedCoupon)
        assertFalse(state.isValidatingCoupon)
        assertEquals(Quote(3_000, 300, 351, 599, 0, 3_650), state.quote)
    }

    @Test
    fun `coupon rejected records the error, stops validating and leaves coupon and price alone`() {
        val applied = reducer.reduce(withCart, CheckoutAction.CouponApplied(save10))
        val validating = reducer.reduce(applied, CheckoutAction.CouponValidationStarted)

        val state = reducer.reduce(validating, CheckoutAction.CouponRejected(CouponError.NETWORK))

        assertEquals(CouponError.NETWORK, state.couponError)
        assertFalse(state.isValidatingCoupon)
        assertEquals(save10, state.appliedCoupon)
        assertEquals(applied.quote, state.quote)
        assertEquals(300, state.quote.discountCents)
    }

    @Test
    fun `cart saved changes nothing`() {
        val start = withCart.copy(appliedCoupon = save10, couponError = CouponError.NETWORK, loyaltyPointsCents = 10)

        assertSame(start, reducer.reduce(start, CheckoutAction.CartSaved))
    }
}
