package com.eliaseguizabal.checkout.data

import com.eliaseguizabal.checkout.domain.CartItem

data class CartSnapshot(val items: List<CartItem>, val couponCode: String?)

interface CartStore {
    /** Drops the quote cached from the last save; it no longer matches the cart. */
    suspend fun clearCachedQuote()
    suspend fun save(snapshot: CartSnapshot)
}
