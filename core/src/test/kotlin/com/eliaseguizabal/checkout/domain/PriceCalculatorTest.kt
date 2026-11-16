package com.eliaseguizabal.checkout.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PriceCalculatorTest {

    private val calculator = PriceCalculator()

    private fun item(price: Long, quantity: Int = 1, sku: String = "sku") =
        CartItem(sku = sku, unitPriceCents = price, quantity = quantity)

    private val tenOffUnder2000ThenCapped = PercentOff(
        code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500,
    )

    // --- empty cart -------------------------------------------------------

    @Test
    fun `empty cart quotes to EMPTY and ignores loyalty points`() {
        assertEquals(Quote.EMPTY, calculator.quote(emptyList(), coupon = null, loyaltyPointsCents = 500))
    }

    // --- subtotal, tax, shipping -----------------------------------------

    @Test
    fun `subtotal multiplies unit price by quantity across lines`() {
        val quote = calculator.quote(
            items = listOf(item(price = 1_000, quantity = 2), item(price = 500)),
            coupon = null,
            loyaltyPointsCents = 0,
        )
        assertEquals(2_500, quote.subtotalCents)
    }

    @Test
    fun `tax is 13 percent of the discounted subtotal`() {
        // 2500 * 13% = 325 exactly
        val quote = calculator.quote(listOf(item(price = 2_500)), coupon = null, loyaltyPointsCents = 0)
        assertEquals(325, quote.taxCents)
    }

    @Test
    fun `tax rounds half up`() {
        // 50 * 13% = 6.5 -> 7
        assertEquals(7, calculator.quote(listOf(item(price = 50)), null, 0).taxCents)
        // 49 * 13% = 6.37 -> 6
        assertEquals(6, calculator.quote(listOf(item(price = 49)), null, 0).taxCents)
    }

    @Test
    fun `shipping is charged at or below the free-shipping threshold`() {
        val quote = calculator.quote(listOf(item(price = 5_000)), null, 0)
        assertEquals(599, quote.shippingCents)
    }

    @Test
    fun `shipping is free strictly above the free-shipping threshold`() {
        val quote = calculator.quote(listOf(item(price = 5_001)), null, 0)
        assertEquals(0, quote.shippingCents)
    }

    @Test
    fun `total is subtotal plus tax plus shipping when no discount or points`() {
        // subtotal 2500, tax 325, shipping 599
        val quote = calculator.quote(listOf(item(price = 2_500)), null, 0)
        assertEquals(3_424, quote.totalCents)
        assertEquals(0, quote.discountCents)
        assertEquals(0, quote.pointsAppliedCents)
    }

    // --- coupons ----------------------------------------------------------

    @Test
    fun `percent coupon takes its percentage of the subtotal`() {
        val quote = calculator.quote(listOf(item(price = 4_000)), tenOffUnder2000ThenCapped, 0)
        assertEquals(400, quote.discountCents)
    }

    @Test
    fun `percent coupon is capped at its maximum discount`() {
        // 10% of 20000 = 2000, capped at 1500
        val quote = calculator.quote(listOf(item(price = 20_000)), tenOffUnder2000ThenCapped, 0)
        assertEquals(1_500, quote.discountCents)
    }

    @Test
    fun `percent coupon is ignored below its minimum subtotal`() {
        val quote = calculator.quote(listOf(item(price = 1_999)), tenOffUnder2000ThenCapped, 0)
        assertEquals(0, quote.discountCents)
    }

    @Test
    fun `amount coupon is capped at the subtotal`() {
        val fiveOff = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)
        val quote = calculator.quote(listOf(item(price = 300)), fiveOff, 0)
        assertEquals(300, quote.discountCents)
        assertEquals(0, quote.taxCents)
        assertEquals(599, quote.totalCents) // only shipping remains
    }

    @Test
    fun `amount coupon is ignored below its minimum subtotal`() {
        val coupon = AmountOff(code = "MIN", minSubtotalCents = 1_000, amountCents = 500)
        assertEquals(0, calculator.quote(listOf(item(price = 999)), coupon, 0).discountCents)
    }

    @Test
    fun `amount coupon is applied when subtotal equals its minimum`() {
        val coupon = AmountOff(code = "MIN", minSubtotalCents = 1_000, amountCents = 500)
        assertEquals(500, calculator.quote(listOf(item(price = 1_000)), coupon, 0).discountCents)
    }

    @Test
    fun `tax and shipping are computed on the discounted amount`() {
        // subtotal 5500, amount coupon 1000 -> discounted 4500
        // tax = 4500 * 13% = 585; discounted is <= 5000 so shipping is 599
        val coupon = AmountOff(code = "TAKE10", minSubtotalCents = 0, amountCents = 1_000)
        val quote = calculator.quote(listOf(item(price = 5_500)), coupon, 0)
        assertEquals(1_000, quote.discountCents)
        assertEquals(585, quote.taxCents)
        assertEquals(599, quote.shippingCents)
        assertEquals(5_684, quote.totalCents) // 4500 + 585 + 599
    }

    @Test
    fun `free-shipping threshold is judged on the discounted amount`() {
        // subtotal 5600 is above the threshold, but coupon 600 -> discounted 5000,
        // which is NOT above it, so shipping is charged
        val coupon = AmountOff(code = "SIX", minSubtotalCents = 0, amountCents = 600)
        assertEquals(599, calculator.quote(listOf(item(price = 5_600)), coupon, 0).shippingCents)
    }

    // --- loyalty points ---------------------------------------------------

    @Test
    fun `loyalty points reduce the total`() {
        // beforePoints = 3424
        val quote = calculator.quote(listOf(item(price = 2_500)), null, loyaltyPointsCents = 1_000)
        assertEquals(1_000, quote.pointsAppliedCents)
        assertEquals(2_424, quote.totalCents)
    }

    @Test
    fun `loyalty points never exceed the amount due`() {
        val quote = calculator.quote(listOf(item(price = 2_500)), null, loyaltyPointsCents = 99_999)
        assertEquals(3_424, quote.pointsAppliedCents)
        assertEquals(0, quote.totalCents)
    }

    @Test
    fun `negative loyalty points are treated as zero`() {
        val quote = calculator.quote(listOf(item(price = 2_500)), null, loyaltyPointsCents = -500)
        assertEquals(0, quote.pointsAppliedCents)
        assertEquals(3_424, quote.totalCents)
    }

    // --- eligibility ------------------------------------------------------

    @Test
    fun `coupon is eligible when subtotal equals its minimum`() {
        assertTrue(calculator.isEligible(tenOffUnder2000ThenCapped, subtotalCents = 2_000))
    }

    @Test
    fun `coupon is not eligible one cent under its minimum`() {
        assertFalse(calculator.isEligible(tenOffUnder2000ThenCapped, subtotalCents = 1_999))
    }
}
