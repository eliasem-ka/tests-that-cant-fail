package com.eliaseguizabal.checkout.oracle

import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.Coupon
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.domain.Quote
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource

class PriceCalculatorOracleTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("cases")
    fun `quote matches the pricing rules`(
        @Suppress("UNUSED_PARAMETER") rule: String,
        items: List<CartItem>,
        coupon: Coupon?,
        points: Long,
        expected: Quote,
    ) {
        assertEquals(expected, PriceCalculator().quote(items, coupon, points))
    }

    companion object {
        @JvmStatic
        fun cases(): List<Arguments> = listOf(
            Arguments.of("rule 1: exactly at minimum is eligible", listOf(item(2000)), SAVE10, 0L, Quote(2000, 200, 234, 599, 0, 2633)),
            Arguments.of("rule 1: one cent below minimum", listOf(item(1999)), SAVE10, 0L, Quote(1999, 0, 260, 599, 0, 2858)),
            Arguments.of("rule 2: percent discount capped", listOf(item(20000)), SAVE10, 0L, Quote(20000, 1500, 2405, 0, 0, 20905)),
            Arguments.of("rule 4: tax after discount", listOf(item(10000)), FIVEOFF, 0L, Quote(10000, 500, 1235, 0, 0, 10735)),
            Arguments.of("rule 3: amount-off never exceeds subtotal", listOf(item(300)), FIVEOFF, 0L, Quote(300, 300, 0, 599, 0, 599)),
            Arguments.of("rule 5: exactly at threshold pays shipping", listOf(item(5000)), null, 0L, Quote(5000, 0, 650, 599, 0, 6249)),
            Arguments.of("rule 5: above threshold ships free", listOf(item(5001)), null, 0L, Quote(5001, 0, 650, 0, 0, 5651)),
            Arguments.of("rule 3: points capped, total never negative", listOf(item(1000)), null, 99_999L, Quote(1000, 0, 130, 599, 1729, 0)),
            Arguments.of("negative points are ignored", listOf(item(1000)), null, -50L, Quote(1000, 0, 130, 599, 0, 1729)),
            Arguments.of("empty cart is all zeros", emptyList<CartItem>(), SAVE10, 500L, Quote.EMPTY),
            Arguments.of("quantity multiplies unit price", listOf(item(1500, quantity = 2)), null, 0L, Quote(3000, 0, 390, 599, 0, 3989)),
        )
    }
}
