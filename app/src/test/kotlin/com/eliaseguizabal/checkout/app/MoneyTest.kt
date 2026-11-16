package com.eliaseguizabal.checkout.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `zero`() = assertEquals("$0.00", formatCents(0))

    @Test
    fun `single digit cents are zero padded`() = assertEquals("$0.05", formatCents(5))

    @Test
    fun `whole dollars`() = assertEquals("$1.00", formatCents(100))

    @Test
    fun `dollars and cents`() = assertEquals("$39.99", formatCents(3_999))

    @Test
    fun `large amounts are not grouped`() = assertEquals("$1234.56", formatCents(123_456))
}
