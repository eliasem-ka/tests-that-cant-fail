package com.eliaseguizabal.checkout.data

import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.Quote
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InMemoryCouponRepositoryTest {

    private val repository = InMemoryCouponRepository()

    @Test
    fun `finds codes regardless of case and surrounding whitespace`() = runTest {
        assertEquals(
            CouponResult.Found(PercentOff("SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)),
            repository.find("  save10 "),
        )
    }

    @Test
    fun `knows every demo coupon`() = runTest {
        assertEquals(
            CouponResult.Found(PercentOff("BIG20", minSubtotalCents = 10_000, percent = 20, maxDiscountCents = 2_500)),
            repository.find("BIG20"),
        )
        assertEquals(CouponResult.Found(AmountOff("FIVEOFF", minSubtotalCents = 0, amountCents = 500)), repository.find("FiveOff"))
    }

    @Test
    fun `unknown code is not found`() = runTest {
        assertEquals(CouponResult.NotFound, repository.find("SAVE100"))
    }

    @Test
    fun `OFFLINE simulates a network error`() = runTest {
        assertEquals(CouponResult.NetworkError, repository.find("offline"))
    }

    @Test
    fun `waits for the configured latency`() = runTest {
        InMemoryCouponRepository(latencyMs = 250).find("SAVE10")
        assertEquals(250, testScheduler.currentTime)
    }
}

class InMemoryCartStoreTest {

    @Test
    fun `save keeps the latest snapshot`() = runTest {
        val store = InMemoryCartStore()
        val first = CartSnapshot(listOf(CartItem("a", 100, 1)), null)
        val second = CartSnapshot(listOf(CartItem("b", 200, 2)), "SAVE10")

        store.save(first)
        store.save(second)

        assertEquals(second, store.lastSnapshot)
    }

    @Test
    fun `clearCachedQuote drops the cached quote`() = runTest {
        val store = InMemoryCartStore().apply { cachedQuote = Quote.EMPTY.copy(totalCents = 1) }

        store.clearCachedQuote()

        assertNull(store.cachedQuote)
    }
}
