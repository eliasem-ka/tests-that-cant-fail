package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutReducerTest {
    private val calc = PriceCalculator()
    private val reducer = CheckoutReducer(calc)
    private val item = CartItem("a", 10_000, 1)

    @Test fun `ItemsChanged stores items and requotes`() {
        val s = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(listOf(item)))
        assertEquals(listOf(item), s.items)
        assertEquals(calc.quote(listOf(item), null, 0), s.quote)
    }

    @Test fun `PointsChanged requotes with points`() {
        val s0 = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(listOf(item)))
        val s = reducer.reduce(s0, CheckoutAction.PointsChanged(300))
        assertEquals(300, s.loyaltyPointsCents)
        assertEquals(300, s.quote.pointsAppliedCents)
    }

    @Test fun `validation started sets flag and clears error`() {
        val s = reducer.reduce(CheckoutState(couponError = CouponError.NETWORK), CheckoutAction.CouponValidationStarted)
        assertTrue(s.isValidatingCoupon)
        assertNull(s.couponError)
    }

    @Test fun `CouponApplied sets coupon, clears flag, requotes`() {
        val coupon = AmountOff("A", 0, 500)
        val s0 = reducer.reduce(CheckoutState(isValidatingCoupon = true), CheckoutAction.ItemsChanged(listOf(item)))
        val s = reducer.reduce(s0, CheckoutAction.CouponApplied(coupon))
        assertEquals(coupon, s.appliedCoupon)
        assertFalse(s.isValidatingCoupon)
        assertEquals(500, s.quote.discountCents)
    }

    @Test fun `CouponRejected sets error and clears flag`() {
        val s = reducer.reduce(CheckoutState(isValidatingCoupon = true), CheckoutAction.CouponRejected(CouponError.NOT_FOUND))
        assertEquals(CouponError.NOT_FOUND, s.couponError)
        assertFalse(s.isValidatingCoupon)
    }

    @Test fun `CartSaved leaves state unchanged`() {
        val s = CheckoutState(items = listOf(item))
        assertEquals(s, reducer.reduce(s, CheckoutAction.CartSaved))
    }
}
