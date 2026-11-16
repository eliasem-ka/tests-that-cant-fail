package com.eliaseguizabal.checkout.data

import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.Quote
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InMemoryTest {
    private val repo = InMemoryCouponRepository(latencyMs = 100)

    @Test fun `finds known coupon case-insensitively and trimmed`() = runTest {
        val r = repo.find("  save10 ") as CouponResult.Found
        assertEquals(PercentOff("SAVE10", 2_000, 10, 1_500), r.coupon)
    }

    @Test fun `finds amount coupon`() = runTest {
        assertEquals(AmountOff("FIVEOFF", 0, 500), (repo.find("FIVEOFF") as CouponResult.Found).coupon)
    }

    @Test fun `unknown code is NotFound`() = runTest {
        assertEquals(CouponResult.NotFound, repo.find("nope"))
    }

    @Test fun `OFFLINE is network error`() = runTest {
        assertEquals(CouponResult.NetworkError, repo.find("offline"))
    }

    @Test fun `simulates latency`() = runTest {
        repo.find("SAVE10")
        assertEquals(100, currentTime)
    }

    @Test fun `cart store saves and clears quote`() = runTest {
        val store = InMemoryCartStore()
        store.cachedQuote = Quote.EMPTY
        val snap = CartSnapshot(emptyList(), "X")
        store.save(snap)
        store.clearCachedQuote()
        assertEquals(snap, store.lastSnapshot)
        assertNull(store.cachedQuote)
    }
}
