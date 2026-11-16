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
    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)
    private val cart = listOf(CartItem("a", 1_000, 2), CartItem("b", 1_000, 1)) // subtotal 3000

    @Test
    fun `ItemsChanged replaces the items and requotes`() {
        val state = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart))

        assertEquals(cart, state.items)
        assertEquals(3_000, state.quote.subtotalCents)
        assertEquals(3_000 + 390 + 599, state.quote.totalCents)
    }

    @Test
    fun `ItemsChanged to an empty cart resets the quote`() {
        val withItems = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart))
        val emptied = reducer.reduce(withItems, CheckoutAction.ItemsChanged(emptyList()))

        assertEquals(Quote.EMPTY, emptied.quote)
    }

    @Test
    fun `PointsChanged stores the points and requotes`() {
        val withItems = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart))
        val state = reducer.reduce(withItems, CheckoutAction.PointsChanged(989))

        assertEquals(989, state.loyaltyPointsCents)
        assertEquals(989, state.quote.pointsAppliedCents)
        assertEquals(3_000, state.quote.totalCents)
    }

    @Test
    fun `CouponValidationStarted sets the flag and clears a previous error`() {
        val before = CheckoutState(items = cart, couponError = CouponError.NOT_FOUND)
        val state = reducer.reduce(before, CheckoutAction.CouponValidationStarted)

        assertTrue(state.isValidatingCoupon)
        assertNull(state.couponError)
        assertEquals(before.quote, state.quote)
    }

    @Test
    fun `CouponApplied stores the coupon, stops validating and discounts the quote`() {
        val validating = reducer.reduce(
            reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart)),
            CheckoutAction.CouponValidationStarted,
        )
        val state = reducer.reduce(validating, CheckoutAction.CouponApplied(save10))

        assertEquals(save10, state.appliedCoupon)
        assertFalse(state.isValidatingCoupon)
        assertEquals(300, state.quote.discountCents)
        assertEquals(2_700 + 351 + 599, state.quote.totalCents)
    }

    @Test
    fun `CouponRejected records the error, stops validating and keeps the coupon already applied`() {
        val applied = reducer.reduce(
            reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart)),
            CheckoutAction.CouponApplied(save10),
        )
        val validating = reducer.reduce(applied, CheckoutAction.CouponValidationStarted)
        val state = reducer.reduce(validating, CheckoutAction.CouponRejected(CouponError.NETWORK))

        assertEquals(CouponError.NETWORK, state.couponError)
        assertFalse(state.isValidatingCoupon)
        assertEquals(save10, state.appliedCoupon)
        assertEquals(applied.quote, state.quote)
    }

    @Test
    fun `items changing after a coupon is applied requote with that coupon`() {
        val applied = reducer.reduce(
            reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart)),
            CheckoutAction.CouponApplied(save10),
        )
        val state = reducer.reduce(applied, CheckoutAction.ItemsChanged(cart + CartItem("c", 1_000, 1)))

        assertEquals(400, state.quote.discountCents)
    }

    @Test
    fun `dropping below the coupon minimum removes the discount from the quote`() {
        val applied = reducer.reduce(
            reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart)),
            CheckoutAction.CouponApplied(save10),
        )
        val state = reducer.reduce(applied, CheckoutAction.ItemsChanged(listOf(CartItem("a", 1_000, 1))))

        assertEquals(0, state.quote.discountCents)
        assertEquals(1_000 + 130 + 599, state.quote.totalCents)
    }

    @Test
    fun `CartSaved leaves the state untouched`() {
        val state = CheckoutState(items = cart, loyaltyPointsCents = 5)
        assertSame(state, reducer.reduce(state, CheckoutAction.CartSaved))
    }
}
