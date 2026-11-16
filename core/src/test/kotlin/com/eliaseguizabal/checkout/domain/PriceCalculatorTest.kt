package com.eliaseguizabal.checkout.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PriceCalculatorTest {

    private val calculator = PriceCalculator()

    private fun cartOf(vararg cents: Long) = cents.mapIndexed { i, c -> CartItem(sku = "sku$i", unitPriceCents = c, quantity = 1) }

    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)
    private val big20 = PercentOff(code = "BIG20", minSubtotalCents = 10_000, percent = 20, maxDiscountCents = 2_500)

    // --- Subtotal ------------------------------------------------------------

    @Test
    fun `subtotal is unit price times quantity summed over lines`() {
        val items = listOf(
            CartItem(sku = "A", unitPriceCents = 250, quantity = 3),
            CartItem(sku = "B", unitPriceCents = 1_000, quantity = 1),
        )
        assertEquals(1_750, calculator.subtotal(items))
        assertEquals(1_750, calculator.quote(items, null, 0).subtotalCents)
    }

    // --- Empty cart ----------------------------------------------------------

    @Test
    fun `empty cart costs nothing - no shipping, no points, no discount`() {
        val coupon = AmountOff(code = "ANY", minSubtotalCents = 0, amountCents = 500)
        assertEquals(Quote.EMPTY, calculator.quote(emptyList(), coupon, loyaltyPointsCents = 300))
        assertEquals(Quote.EMPTY, calculator.quote(emptyList(), null, 0))
    }

    // --- Tax -----------------------------------------------------------------

    @Test
    fun `plain cart pays 13 percent tax and the shipping fee`() {
        assertEquals(
            Quote(subtotalCents = 3_000, discountCents = 0, taxCents = 390, shippingCents = 599, pointsAppliedCents = 0, totalCents = 3_989),
            calculator.quote(cartOf(3_000), null, 0),
        )
    }

    @Test
    fun `tax rounds half a cent up`() {
        // 150 * 13% = 19.5 -> 20
        assertEquals(Quote(150, 0, 20, 599, 0, 769), calculator.quote(cartOf(150), null, 0))
    }

    @Test
    fun `tax rounds less than half a cent down`() {
        // 101 * 13% = 13.13 -> 13
        assertEquals(Quote(101, 0, 13, 599, 0, 713), calculator.quote(cartOf(101), null, 0))
    }

    @Test
    fun `tax is charged on the discounted amount, not the subtotal`() {
        // 3000 - 300 = 2700; 2700 * 13% = 351
        assertEquals(Quote(3_000, 300, 351, 599, 0, 3_650), calculator.quote(cartOf(3_000), save10, 0))
    }

    // --- Shipping ------------------------------------------------------------

    @Test
    fun `shipping is charged at exactly the free-shipping threshold`() {
        assertEquals(Quote(5_000, 0, 650, 599, 0, 6_249), calculator.quote(cartOf(5_000), null, 0))
    }

    @Test
    fun `shipping is free one cent above the threshold`() {
        assertEquals(Quote(5_001, 0, 650, 0, 0, 5_651), calculator.quote(cartOf(5_001), null, 0))
    }

    @Test
    fun `free shipping is judged on the discounted amount`() {
        // Subtotal 55.00 is above 50.00, but 45.00 after the coupon is not.
        val tenOff = AmountOff(code = "TENOFF", minSubtotalCents = 0, amountCents = 1_000)
        assertEquals(Quote(5_500, 1_000, 585, 599, 0, 5_684), calculator.quote(cartOf(5_500), tenOff, 0))
    }

    // --- Coupons -------------------------------------------------------------

    @Test
    fun `percent coupon below its cap gives the full percentage`() {
        // 20% of 100.00 = 20.00, under the 25.00 cap
        assertEquals(Quote(10_000, 2_000, 1_040, 0, 0, 9_040), calculator.quote(cartOf(10_000), big20, 0))
    }

    @Test
    fun `percent coupon never discounts more than its cap`() {
        // 20% of 200.00 = 40.00, capped at 25.00
        assertEquals(Quote(20_000, 2_500, 2_275, 0, 0, 19_775), calculator.quote(cartOf(20_000), big20, 0))
    }

    @Test
    fun `percent discount rounds fractional cents down`() {
        val tenPercent = PercentOff(code = "TEN", minSubtotalCents = 0, percent = 10, maxDiscountCents = 10_000)
        // 10% of 19.99 = 1.999 -> 1.99
        assertEquals(Quote(1_999, 199, 234, 599, 0, 2_633), calculator.quote(cartOf(1_999), tenPercent, 0))
    }

    @Test
    fun `amount coupon never discounts more than the subtotal`() {
        val fiveOff = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)
        assertEquals(Quote(300, 300, 0, 599, 0, 599), calculator.quote(cartOf(300), fiveOff, 0))
    }

    @Test
    fun `amount coupon smaller than the subtotal takes off exactly its amount`() {
        val fiveOff = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)
        // 2500 * 13% = 325
        assertEquals(Quote(3_000, 500, 325, 599, 0, 3_424), calculator.quote(cartOf(3_000), fiveOff, 0))
    }

    @Test
    fun `coupon whose minimum the cart no longer meets gives no discount`() {
        assertEquals(Quote(3_000, 0, 390, 599, 0, 3_989), calculator.quote(cartOf(3_000), big20, 0))
    }

    @Test
    fun `coupon applies when the subtotal is exactly its minimum`() {
        val exact = AmountOff(code = "EXACT", minSubtotalCents = 3_000, amountCents = 500)
        assertEquals(500, calculator.quote(cartOf(3_000), exact, 0).discountCents)
        assertEquals(0, calculator.quote(cartOf(2_999), exact, 0).discountCents)
    }

    @Test
    fun `isEligible is inclusive of the minimum`() {
        val exact = AmountOff(code = "EXACT", minSubtotalCents = 3_000, amountCents = 500)
        assertTrue(calculator.isEligible(exact, 3_000))
        assertTrue(calculator.isEligible(exact, 3_001))
        assertFalse(calculator.isEligible(exact, 2_999))
    }

    // --- Loyalty points ------------------------------------------------------

    @Test
    fun `points come off the total after tax and shipping`() {
        assertEquals(Quote(3_000, 0, 390, 599, 989, 3_000), calculator.quote(cartOf(3_000), null, 989))
    }

    @Test
    fun `points never take the total below zero`() {
        assertEquals(Quote(3_000, 0, 390, 599, 3_989, 0), calculator.quote(cartOf(3_000), null, 1_000_000))
    }

    @Test
    fun `negative points are ignored`() {
        assertEquals(Quote(3_000, 0, 390, 599, 0, 3_989), calculator.quote(cartOf(3_000), null, -500))
    }
}
