package com.eliaseguizabal.checkout.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

/**
 * Expected values are worked out by hand from the rules (13% tax rounded half-up on the
 * discounted subtotal, $5.99 shipping unless the discounted subtotal is above $50.00),
 * never by calling the calculator a second time.
 */
class PriceCalculatorTest {

    private val calculator = PriceCalculator()

    private fun item(cents: Long, quantity: Int = 1, sku: String = "sku-$cents") =
        CartItem(sku = sku, unitPriceCents = cents, quantity = quantity)

    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)
    private val fiveOff = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)

    @Test
    fun `empty cart is the empty quote even with a coupon and points`() {
        assertEquals(Quote.EMPTY, calculator.quote(emptyList(), fiveOff, loyaltyPointsCents = 1_000))
    }

    @Test
    fun `subtotal multiplies unit price by quantity and sums lines`() {
        val quote = calculator.quote(listOf(item(1_000, quantity = 2), item(500)), coupon = null, loyaltyPointsCents = 0)

        assertEquals(
            Quote(
                subtotalCents = 2_500,
                discountCents = 0,
                taxCents = 325,
                shippingCents = 599,
                pointsAppliedCents = 0,
                totalCents = 3_424,
            ),
            quote,
        )
    }

    @Nested
    inner class Tax {
        private fun taxOn(subtotal: Long) = calculator.quote(listOf(item(subtotal)), null, 0).taxCents

        @Test
        fun `exact half cent rounds up`() {
            // 1050 * 13% = 136.5
            assertEquals(137, taxOn(1_050))
        }

        @Test
        fun `below half cent rounds down`() {
            // 1049 * 13% = 136.37
            assertEquals(136, taxOn(1_049))
        }

        @Test
        fun `above half cent rounds up`() {
            // 1046 * 13% = 135.98
            assertEquals(136, taxOn(1_046))
        }

        @Test
        fun `tax is charged on the discounted subtotal`() {
            // 3000 - 300 = 2700; 2700 * 13% = 351
            val quote = calculator.quote(listOf(item(3_000)), save10, 0)
            assertEquals(351, quote.taxCents)
        }
    }

    @Nested
    inner class Shipping {
        @Test
        fun `exactly fifty dollars still pays shipping`() {
            val quote = calculator.quote(listOf(item(5_000)), null, 0)
            assertEquals(599, quote.shippingCents)
        }

        @Test
        fun `one cent above fifty dollars ships free`() {
            val quote = calculator.quote(listOf(item(5_001)), null, 0)
            assertEquals(0, quote.shippingCents)
        }

        @Test
        fun `a discount that drops the cart to fifty or below brings shipping back`() {
            // 5200 - 500 = 4700 -> pays shipping; tax 611
            val quote = calculator.quote(listOf(item(5_200)), fiveOff, 0)

            assertEquals(500, quote.discountCents)
            assertEquals(611, quote.taxCents)
            assertEquals(599, quote.shippingCents)
            assertEquals(5_910, quote.totalCents)
        }
    }

    @Nested
    inner class Coupons {
        @Test
        fun `percent off takes the percentage of the subtotal`() {
            val quote = calculator.quote(listOf(item(3_000)), save10, 0)

            assertEquals(300, quote.discountCents)
            assertEquals(2_700 + 351 + 599, quote.totalCents)
        }

        @Test
        fun `percent off is capped at its maximum`() {
            // 10% of 20000 = 2000, capped to 1500; 18500 -> tax 2405, free shipping
            val quote = calculator.quote(listOf(item(20_000)), save10, 0)

            assertEquals(1_500, quote.discountCents)
            assertEquals(2_405, quote.taxCents)
            assertEquals(0, quote.shippingCents)
            assertEquals(20_905, quote.totalCents)
        }

        @Test
        fun `percent off truncates fractional cents`() {
            // 10% of 2005 = 200.5
            assertEquals(200, calculator.quote(listOf(item(2_005)), save10, 0).discountCents)
        }

        @Test
        fun `coupon applies at exactly its minimum`() {
            assertEquals(200, calculator.quote(listOf(item(2_000)), save10, 0).discountCents)
        }

        @Test
        fun `coupon below its minimum gives no discount`() {
            val quote = calculator.quote(listOf(item(1_999)), save10, 0)

            assertEquals(0, quote.discountCents)
            assertEquals(1_999 + 260 + 599, quote.totalCents)
        }

        @Test
        fun `amount off never exceeds the subtotal`() {
            // 300 - 300 = 0 -> no tax, shipping still charged
            val quote = calculator.quote(listOf(item(300)), fiveOff, 0)

            assertEquals(300, quote.discountCents)
            assertEquals(0, quote.taxCents)
            assertEquals(599, quote.shippingCents)
            assertEquals(599, quote.totalCents)
        }

        @Test
        fun `isEligible compares against the minimum inclusively`() {
            assertFalse(calculator.isEligible(save10, 1_999))
            assertTrue(calculator.isEligible(save10, 2_000))
            assertTrue(calculator.isEligible(fiveOff, 0))
        }
    }

    @Nested
    inner class LoyaltyPoints {
        private val cart = listOf(item(2_500)) // total before points: 2500 + 325 + 599 = 3424

        @Test
        fun `points come off the total after tax and shipping`() {
            val quote = calculator.quote(cart, null, loyaltyPointsCents = 424)

            assertEquals(424, quote.pointsAppliedCents)
            assertEquals(3_000, quote.totalCents)
            assertEquals(325, quote.taxCents) // points do not reduce tax
        }

        @Test
        fun `points are capped at the total so it never goes negative`() {
            val quote = calculator.quote(cart, null, loyaltyPointsCents = 10_000)

            assertEquals(3_424, quote.pointsAppliedCents)
            assertEquals(0, quote.totalCents)
        }

        @Test
        fun `negative points are ignored`() {
            val quote = calculator.quote(cart, null, loyaltyPointsCents = -100)

            assertEquals(0, quote.pointsAppliedCents)
            assertEquals(3_424, quote.totalCents)
        }
    }
}
