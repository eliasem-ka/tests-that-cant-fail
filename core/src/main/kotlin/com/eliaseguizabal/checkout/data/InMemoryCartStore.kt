package com.eliaseguizabal.checkout.data

import com.eliaseguizabal.checkout.domain.Quote

class InMemoryCartStore : CartStore {
    var lastSnapshot: CartSnapshot? = null
        private set
    var cachedQuote: Quote? = null

    override suspend fun clearCachedQuote() {
        cachedQuote = null
    }

    override suspend fun save(snapshot: CartSnapshot) {
        lastSnapshot = snapshot
    }
}
