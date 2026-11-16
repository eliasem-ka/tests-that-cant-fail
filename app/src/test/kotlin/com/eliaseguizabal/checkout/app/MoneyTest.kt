package com.eliaseguizabal.checkout.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `zero is formatted with two decimal places`() {
        assertEquals("$0.00", formatCents(0))
    }

    @Test
    fun `cents below one dollar are zero padded`() {
        assertEquals("$0.05", formatCents(5))
    }

    @Test
    fun `whole and fractional dollars are both shown`() {
        assertEquals("$19.99", formatCents(1_999))
        assertEquals("$1000.00", formatCents(100_000))
    }

    @Test
    fun `large amounts keep every digit`() {
        assertEquals("$1234.56", formatCents(123_456))
    }
}
