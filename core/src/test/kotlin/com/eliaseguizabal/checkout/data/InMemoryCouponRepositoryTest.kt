package com.eliaseguizabal.checkout.data

import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.PercentOff
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class InMemoryCouponRepositoryTest {

    private val repository = InMemoryCouponRepository(latencyMs = 0)

    @Test
    fun `catalog holds the three published coupons with their terms`() = runTest {
        assertEquals(
            CouponResult.Found(PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)),
            repository.find("SAVE10"),
        )
        assertEquals(
            CouponResult.Found(PercentOff(code = "BIG20", minSubtotalCents = 10_000, percent = 20, maxDiscountCents = 2_500)),
            repository.find("BIG20"),
        )
        assertEquals(
            CouponResult.Found(AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)),
            repository.find("FIVEOFF"),
        )
    }

    @Test
    fun `codes are matched ignoring surrounding spaces and case`() = runTest {
        val expected = repository.find("SAVE10")
        assertEquals(expected, repository.find("  save10 "))
        assertEquals(expected, repository.find("Save10"))
    }

    @Test
    fun `unknown code is not found`() = runTest {
        assertEquals(CouponResult.NotFound, repository.find("NOPE"))
        assertEquals(CouponResult.NotFound, repository.find(""))
    }

    @Test
    fun `OFFLINE simulates a network failure in any case and spacing`() = runTest {
        assertEquals(CouponResult.NetworkError, repository.find("OFFLINE"))
        assertEquals(CouponResult.NetworkError, repository.find(" offline "))
    }
}
