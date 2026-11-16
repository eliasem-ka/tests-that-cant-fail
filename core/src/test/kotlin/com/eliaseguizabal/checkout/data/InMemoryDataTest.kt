@file:OptIn(ExperimentalCoroutinesApi::class)

package com.eliaseguizabal.checkout.data

import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.Quote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InMemoryCartStoreTest {

    @Test
    fun `save records the snapshot`() = runTest {
        val store = InMemoryCartStore()
        val snapshot = CartSnapshot(listOf(CartItem("A", 100, 1)), "SAVE10")

        store.save(snapshot)

        assertEquals(snapshot, store.lastSnapshot)
    }

    @Test
    fun `no snapshot exists before the first save`() {
        assertNull(InMemoryCartStore().lastSnapshot)
    }

    @Test
    fun `clearCachedQuote drops the cached quote`() = runTest {
        val store = InMemoryCartStore().apply { cachedQuote = Quote.EMPTY }

        store.clearCachedQuote()

        assertNull(store.cachedQuote)
    }
}

class InMemoryCouponRepositoryTest {

    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)
    private val repo = InMemoryCouponRepository(latencyMs = 0)

    @Test
    fun `finds a known code`() = runTest {
        assertEquals(CouponResult.Found(save10), repo.find("SAVE10"))
    }

    @Test
    fun `lookup ignores case and surrounding whitespace`() = runTest {
        assertEquals(CouponResult.Found(save10), repo.find("  save10 "))
    }

    @Test
    fun `finds the fixed-amount coupon`() = runTest {
        assertEquals(
            CouponResult.Found(AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)),
            repo.find("fiveoff"),
        )
    }

    @Test
    fun `unknown code is NotFound`() = runTest {
        assertEquals(CouponResult.NotFound, repo.find("NOPE"))
    }

    @Test
    fun `OFFLINE simulates a network error regardless of case`() = runTest {
        assertEquals(CouponResult.NetworkError, repo.find("offline"))
        assertEquals(CouponResult.NetworkError, repo.find(" OFFLINE "))
    }

    @Test
    fun `lookup takes the configured latency`() = runTest {
        val slow = InMemoryCouponRepository(latencyMs = 800)
        val start = testScheduler.currentTime

        slow.find("SAVE10")

        assertEquals(800L, testScheduler.currentTime - start)
    }
}
