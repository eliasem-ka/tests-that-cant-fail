package com.eliaseguizabal.checkout.app

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {
    @Test fun zero() = assertEquals("$0.00", formatCents(0))
    @Test fun padsCents() = assertEquals("$1.05", formatCents(105))
    @Test fun large() = assertEquals("$12345.67", formatCents(1_234_567))
}
