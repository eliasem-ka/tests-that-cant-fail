package com.eliaseguizabal.checkout.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PriceCalculatorTest {
    private val calc = PriceCalculator()
    private fun item(price: Long, qty: Int = 1, sku: String = "a") = CartItem(sku, price, qty)

    @Test fun `empty cart yields EMPTY quote`() {
        assertEquals(Quote.EMPTY, calc.quote(emptyList(), PercentOff("X", 0, 10, 100), 500))
    }

    @Test fun `no coupon below free shipping threshold adds shipping and tax`() {
        val q = calc.quote(listOf(item(1_000, 2)), null, 0)
        assertEquals(2_000, q.subtotalCents)
        assertEquals(0, q.discountCents)
        assertEquals(260, q.taxCents)
        assertEquals(599, q.shippingCents)
        assertEquals(2_859, q.totalCents)
    }

    @Test fun `tax rounds half up`() {
        // 150 * 13% = 19.5 -> 20
        assertEquals(20, calc.quote(listOf(item(150)), null, 0).taxCents)
        // 110 * 13% = 14.3 -> 14
        assertEquals(14, calc.quote(listOf(item(110)), null, 0).taxCents)
    }

    @Test fun `shipping is free only strictly above threshold`() {
        assertEquals(599, calc.quote(listOf(item(5_000)), null, 0).shippingCents)
        assertEquals(0, calc.quote(listOf(item(5_001)), null, 0).shippingCents)
    }

    @Test fun `free shipping is evaluated after discount`() {
        val q = calc.quote(listOf(item(5_200)), AmountOff("F", 0, 500), 0)
        assertEquals(4_700, q.subtotalCents - q.discountCents)
        assertEquals(599, q.shippingCents)
    }

    @Test fun `percent coupon applies percentage`() {
        val q = calc.quote(listOf(item(3_000)), PercentOff("P", 2_000, 10, 1_500), 0)
        assertEquals(300, q.discountCents)
    }

    @Test fun `percent coupon is capped`() {
        val q = calc.quote(listOf(item(20_000)), PercentOff("P", 0, 20, 2_500), 0)
        assertEquals(2_500, q.discountCents)
    }

    @Test fun `amount coupon is capped at subtotal`() {
        val q = calc.quote(listOf(item(300)), AmountOff("A", 0, 500), 0)
        assertEquals(300, q.discountCents)
        assertEquals(0, q.taxCents)
    }

    @Test fun `ineligible coupon gives no discount`() {
        val q = calc.quote(listOf(item(1_000)), PercentOff("P", 2_000, 10, 1_500), 0)
        assertEquals(0, q.discountCents)
    }

    @Test fun `isEligible is inclusive of minimum`() {
        val c = AmountOff("A", 1_000, 100)
        assertTrue(calc.isEligible(c, 1_000))
        assertFalse(calc.isEligible(c, 999))
    }

    @Test fun `points are capped at amount owed`() {
        val q = calc.quote(listOf(item(1_000)), null, 1_000_000)
        assertEquals(1_000 + 130 + 599, q.pointsAppliedCents)
        assertEquals(0, q.totalCents)
    }

    @Test fun `points reduce total`() {
        val q = calc.quote(listOf(item(10_000)), null, 400)
        assertEquals(400, q.pointsAppliedCents)
        assertEquals(10_000 + 1_300 - 400, q.totalCents)
    }

    @Test fun `negative points are ignored`() {
        val q = calc.quote(listOf(item(10_000)), null, -50)
        assertEquals(0, q.pointsAppliedCents)
        assertEquals(11_300, q.totalCents)
    }

    @Test fun `subtotal sums quantity times price across items`() {
        val q = calc.quote(listOf(item(100, 3, "a"), item(250, 2, "b")), null, 0)
        assertEquals(800, q.subtotalCents)
    }
}
