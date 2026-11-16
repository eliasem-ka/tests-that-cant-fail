package com.eliaseguizabal.checkout.oracle

import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CartMiddleware
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutReducer
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CheckoutStore
import com.eliaseguizabal.checkout.store.CouponError
import com.eliaseguizabal.checkout.store.CouponMiddleware
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Every call to [find] waits until the test releases it, so in-flight windows are real. */
private class GatedCouponRepository : CouponRepository {
    private val results = mapOf(
        "SAVE10" to CouponResult.Found(SAVE10),
        "FIVEOFF" to CouponResult.Found(FIVEOFF),
        "OFFLINE" to CouponResult.NetworkError,
    )
    val requested = mutableListOf<String>()
    private val gates = mutableListOf<CompletableDeferred<Unit>>()

    override suspend fun find(code: String): CouponResult {
        requested += code
        val gate = CompletableDeferred<Unit>()
        gates += gate
        gate.await()
        return results[code] ?: CouponResult.NotFound
    }

    fun releaseAll() = gates.forEach { it.complete(Unit) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CouponMiddlewareOracleTest {

    private val repository = GatedCouponRepository()
    private val calculator = PriceCalculator()

    private fun TestScope.store() = CheckoutStore(
        CheckoutState(),
        CheckoutReducer(calculator),
        listOf(CartMiddleware(), CouponMiddleware(repository, calculator)),
        this,
    )

    private fun TestScope.settle() {
        runCurrent()
        repository.releaseAll()
        advanceUntilIdle()
    }

    @Test
    fun `valid coupon applies and discounts the total`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(2000)))
        store.dispatch(CheckoutIntent.ApplyCoupon("SAVE10"))
        settle()

        assertEquals(SAVE10, store.state.value.appliedCoupon)
        assertEquals(2633, store.state.value.quote.totalCents)
        assertNull(store.state.value.couponError)
    }

    @Test
    fun `coupon below its minimum is rejected with BELOW_MINIMUM and no discount`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(1999)))
        store.dispatch(CheckoutIntent.ApplyCoupon("SAVE10"))
        settle()

        assertEquals(CouponError.BELOW_MINIMUM, store.state.value.couponError)
        assertNull(store.state.value.appliedCoupon)
        assertEquals(2858, store.state.value.quote.totalCents)
    }

    @Test
    fun `unknown code is rejected with NOT_FOUND`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(2000)))
        store.dispatch(CheckoutIntent.ApplyCoupon("NOPE"))
        settle()

        assertEquals(CouponError.NOT_FOUND, store.state.value.couponError)
        assertNull(store.state.value.appliedCoupon)
    }

    @Test
    fun `network failure shows NETWORK and keeps the previous coupon and total`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(2000)))
        store.dispatch(CheckoutIntent.ApplyCoupon("SAVE10"))
        settle()
        store.dispatch(CheckoutIntent.ApplyCoupon("OFFLINE"))
        settle()

        val state = store.state.value
        assertEquals(CouponError.NETWORK, state.couponError)
        assertEquals(SAVE10, state.appliedCoupon)
        assertEquals(2633, state.quote.totalCents)
        assertFalse(state.isValidatingCoupon)
    }

    @Test
    fun `second apply while the first is in flight is ignored`() = runTest {
        val store = store()
        store.dispatch(CheckoutIntent.AddItem(item(2000)))
        store.dispatch(CheckoutIntent.ApplyCoupon("SAVE10"))
        runCurrent()
        assertTrue(store.state.value.isValidatingCoupon)

        store.dispatch(CheckoutIntent.ApplyCoupon("FIVEOFF"))
        runCurrent()
        assertEquals(listOf("SAVE10"), repository.requested)

        settle()
        assertEquals(SAVE10, store.state.value.appliedCoupon)
        assertEquals(2633, store.state.value.quote.totalCents)
    }
}
