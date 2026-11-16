@file:OptIn(ExperimentalCoroutinesApi::class)

package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.CartStore
import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.Quote
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay

/** Returns [result] for every lookup and records the codes it was asked for. */
internal class FakeCouponRepository(
    var result: CouponResult = CouponResult.NotFound,
    private val latencyMs: Long = 0,
) : CouponRepository {
    val requestedCodes = mutableListOf<String>()

    override suspend fun find(code: String): CouponResult {
        requestedCodes += code
        if (latencyMs > 0) delay(latencyMs)
        return result
    }
}

/** Records every call in order so tests can assert sequencing. */
internal class RecordingCartStore : CartStore {
    val calls = mutableListOf<String>()
    val saved = mutableListOf<CartSnapshot>()
    var cachedQuote: Quote? = Quote.EMPTY

    override suspend fun clearCachedQuote() {
        calls += "clearCachedQuote"
        cachedQuote = null
    }

    override suspend fun save(snapshot: CartSnapshot) {
        calls += "save"
        saved += snapshot
    }
}
