package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.domain.Quote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CheckoutReducerTest {

    private val reducer = CheckoutReducer(PriceCalculator())
    private val oneThousand = CartItem(sku = "A", unitPriceCents = 1_000, quantity = 1)

    @Test
    fun `ItemsChanged replaces items and requotes`() {
        // subtotal 1000, tax 130, shipping 599
        val next = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(listOf(oneThousand)))

        assertEquals(listOf(oneThousand), next.items)
        assertEquals(1_000, next.quote.subtotalCents)
        assertEquals(130, next.quote.taxCents)
        assertEquals(599, next.quote.shippingCents)
        assertEquals(1_729, next.quote.totalCents)
    }

    @Test
    fun `ItemsChanged to an empty cart resets the quote`() {
        val filled = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(listOf(oneThousand)))
        val emptied = reducer.reduce(filled, CheckoutAction.ItemsChanged(emptyList()))

        assertEquals(Quote.EMPTY, emptied.quote)
    }

    @Test
    fun `PointsChanged stores points and requotes`() {
        val withItems = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(listOf(oneThousand)))
        val next = reducer.reduce(withItems, CheckoutAction.PointsChanged(500))

        assertEquals(500, next.loyaltyPointsCents)
        assertEquals(500, next.quote.pointsAppliedCents)
        assertEquals(1_229, next.quote.totalCents) // 1729 - 500
    }

    @Test
    fun `CouponValidationStarted marks validating and clears the previous error`() {
        val failed = CheckoutState(couponError = CouponError.NETWORK)
        val next = reducer.reduce(failed, CheckoutAction.CouponValidationStarted)

        assertTrue(next.isValidatingCoupon)
        assertNull(next.couponError)
    }

    @Test
    fun `CouponApplied stores the coupon, stops validating and requotes`() {
        val coupon = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)
        val cart = CartItem(sku = "A", unitPriceCents = 10_000, quantity = 1)
        val validating = reducer.reduce(
            CheckoutState(items = listOf(cart), isValidatingCoupon = true),
            CheckoutAction.CouponApplied(coupon),
        )

        assertEquals(coupon, validating.appliedCoupon)
        assertEquals(false, validating.isValidatingCoupon)
        assertEquals(500, validating.quote.discountCents)
        // discounted 9500: tax 1235, no shipping (above threshold) -> total 10735
        assertEquals(10_735, validating.quote.totalCents)
    }

    @Test
    fun `CouponRejected stores the error and stops validating`() {
        val next = reducer.reduce(
            CheckoutState(isValidatingCoupon = true),
            CheckoutAction.CouponRejected(CouponError.NOT_FOUND),
        )

        assertEquals(CouponError.NOT_FOUND, next.couponError)
        assertEquals(false, next.isValidatingCoupon)
    }

    @Test
    fun `CartSaved leaves state untouched`() {
        val state = CheckoutState(items = listOf(oneThousand))
        assertSame(state, reducer.reduce(state, CheckoutAction.CartSaved))
    }
}
