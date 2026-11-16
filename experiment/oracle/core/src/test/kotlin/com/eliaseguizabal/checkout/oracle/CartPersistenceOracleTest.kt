package com.eliaseguizabal.checkout.oracle

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.CartStore
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CartPersistenceMiddleware
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutReducer
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CheckoutStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

private class RecordingCartStore : CartStore {
    val calls = mutableListOf<String>()
    override suspend fun clearCachedQuote() {
        calls += "clear"
    }
    override suspend fun save(snapshot: CartSnapshot) {
        calls += "save:" + snapshot.items.joinToString(",") { it.sku } + ":" + snapshot.couponCode
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CartPersistenceOracleTest {

    @Test
    fun `saving clears the cached quote before persisting the cart`() = runTest {
        val cartStore = RecordingCartStore()
        val initial = CheckoutState(items = listOf(item(2000)), appliedCoupon = SAVE10)
        val store = CheckoutStore(
            initial,
            CheckoutReducer(PriceCalculator()),
            listOf(CartPersistenceMiddleware(cartStore)),
            this,
        )

        store.dispatch(CheckoutIntent.SaveCart)
        advanceUntilIdle()

        assertEquals(listOf("clear", "save:A:SAVE10"), cartStore.calls)
    }
}
