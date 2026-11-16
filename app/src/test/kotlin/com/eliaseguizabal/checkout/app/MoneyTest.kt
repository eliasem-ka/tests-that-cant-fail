package com.eliaseguizabal.checkout.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `formats cents as dollars with two cent digits`() {
        assertEquals("$0.00", formatCents(0))
        assertEquals("$0.05", formatCents(5))
        assertEquals("$5.99", formatCents(599))
        assertEquals("$30.10", formatCents(3_010))
        assertEquals("$1234.56", formatCents(123_456))
    }
}
