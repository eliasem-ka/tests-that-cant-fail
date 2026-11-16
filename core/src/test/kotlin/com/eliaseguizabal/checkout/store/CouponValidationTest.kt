package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.Coupon
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.PriceCalculator
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.util.concurrent.atomic.AtomicInteger

class CouponValidationTest {

    // Subtotal of 30.00.
    private val cart = listOf(
        CartItem(sku = "A", unitPriceCents = 1_000, quantity = 2),
        CartItem(sku = "B", unitPriceCents = 1_000, quantity = 1),
    )

    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)
    private val big20 = PercentOff(code = "BIG20", minSubtotalCents = 10_000, percent = 20, maxDiscountCents = 2_500)
    private val exactlyMin = AmountOff(code = "EXACT", minSubtotalCents = 3_000, amountCents = 500)
    private val justAbove = AmountOff(code = "ABOVE", minSubtotalCents = 3_001, amountCents = 500)

    /** Answers from a fixed table; codes listed in [gates] suspend until their gate is completed. */
    private inner class FakeRepository(
        private val results: Map<String, CouponResult>,
        val gates: Map<String, CompletableDeferred<Unit>> = emptyMap(),
    ) : CouponRepository {
        val calls = AtomicInteger(0)
        override suspend fun find(code: String): CouponResult {
            calls.incrementAndGet()
            gates[code]?.await()
            return results[code] ?: CouponResult.NotFound
        }
    }

    private fun defaultRepository() = FakeRepository(
        mapOf(
            save10.code to CouponResult.Found(save10),
            big20.code to CouponResult.Found(big20),
            exactlyMin.code to CouponResult.Found(exactlyMin),
            justAbove.code to CouponResult.Found(justAbove),
            "OFFLINE" to CouponResult.NetworkError,
        ),
    )

    private fun store(
        scope: CoroutineScope,
        repository: CouponRepository,
        after: List<Middleware> = emptyList(),
    ): CheckoutStore {
        val calculator = PriceCalculator()
        val reducer = CheckoutReducer(calculator)
        val initial = reducer.reduce(CheckoutState(), CheckoutAction.ItemsChanged(cart))
        return CheckoutStore(
            initial = initial,
            reducer = reducer,
            middlewares = listOf(CartMiddleware(), CouponMiddleware(repository, calculator)) + after,
            scope = scope,
        )
    }

    private fun TestScope.apply(store: CheckoutStore, code: String) {
        store.dispatch(CheckoutIntent.ApplyCoupon(code))
        runCurrent()
    }

    // --- 1. Minimum subtotal -------------------------------------------------

    @Test
    fun `valid code below the minimum subtotal is rejected with BELOW_MINIMUM`() = runTest {
        val store = store(backgroundScope, defaultRepository())

        apply(store, big20.code)

        val state = store.state.value
        assertEquals(CouponError.BELOW_MINIMUM, state.couponError)
        assertNull(state.appliedCoupon)
        assertEquals(0, state.quote.discountCents)
        assertFalse(state.isValidatingCoupon)
    }

    @Test
    fun `subtotal exactly at the minimum is accepted`() = runTest {
        val store = store(backgroundScope, defaultRepository())

        apply(store, exactlyMin.code)

        assertEquals(exactlyMin, store.state.value.appliedCoupon)
        assertNull(store.state.value.couponError)
        assertEquals(500, store.state.value.quote.discountCents)
    }

    @Test
    fun `subtotal one cent below the minimum is rejected`() = runTest {
        val store = store(backgroundScope, defaultRepository())

        apply(store, justAbove.code)

        assertEquals(CouponError.BELOW_MINIMUM, store.state.value.couponError)
        assertNull(store.state.value.appliedCoupon)
    }

    @Test
    fun `minimum is checked against the cart at validation time`() = runTest {
        val store = store(backgroundScope, defaultRepository())
        store.dispatch(CheckoutIntent.AddItem(CartItem(sku = "C", unitPriceCents = 1, quantity = 1)))
        runCurrent()

        apply(store, justAbove.code) // 30.01 now meets the 30.01 minimum

        assertEquals(justAbove, store.state.value.appliedCoupon)
    }

    @Test
    fun `minimum is checked against the cart as it is when the lookup returns`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(
            results = mapOf(justAbove.code to CouponResult.Found(justAbove)),
            gates = mapOf(justAbove.code to gate),
        )
        val store = store(backgroundScope, repository)

        apply(store, justAbove.code) // lookup in flight, cart is 30.00
        store.dispatch(CheckoutIntent.AddItem(CartItem(sku = "C", unitPriceCents = 1, quantity = 1)))
        runCurrent() // cart is now 30.01
        gate.complete(Unit)
        runCurrent()

        assertEquals(justAbove, store.state.value.appliedCoupon)
        assertNull(store.state.value.couponError)
        assertEquals(500, store.state.value.quote.discountCents)
    }

    @Test
    fun `other intents never start a coupon lookup`() = runTest {
        val repository = defaultRepository()
        val store = store(backgroundScope, repository)

        store.dispatch(CheckoutIntent.AddItem(CartItem(sku = "C", unitPriceCents = 1, quantity = 1)))
        store.dispatch(CheckoutIntent.UsePoints(10))
        store.dispatch(CheckoutIntent.SaveCart)
        runCurrent()

        assertEquals(0, repository.calls.get())
        assertNull(store.state.value.appliedCoupon)
        assertNull(store.state.value.couponError)
        assertFalse(store.state.value.isValidatingCoupon)
    }

    // --- 2. Network vs. not found --------------------------------------------

    @Test
    fun `network failure is rejected with NETWORK`() = runTest {
        val store = store(backgroundScope, defaultRepository())

        apply(store, "OFFLINE")

        assertEquals(CouponError.NETWORK, store.state.value.couponError)
        assertFalse(store.state.value.isValidatingCoupon)
    }

    @Test
    fun `unknown code is still rejected with NOT_FOUND`() = runTest {
        val store = store(backgroundScope, defaultRepository())

        apply(store, "NOPE")

        assertEquals(CouponError.NOT_FOUND, store.state.value.couponError)
    }

    @Test
    fun `valid code above the minimum is applied with its discount`() = runTest {
        val store = store(backgroundScope, defaultRepository())

        apply(store, save10.code)

        assertEquals(save10, store.state.value.appliedCoupon)
        assertNull(store.state.value.couponError)
        assertEquals(300, store.state.value.quote.discountCents)
    }

    // --- 3. Rejection keeps the applied coupon -------------------------------

    @ParameterizedTest
    @EnumSource(CouponError::class)
    fun `rejected code keeps the previously applied coupon and its discount`(error: CouponError) = runTest {
        val rejectedCode = when (error) {
            CouponError.NOT_FOUND -> "NOPE"
            CouponError.BELOW_MINIMUM -> big20.code
            CouponError.NETWORK -> "OFFLINE"
        }
        val store = store(backgroundScope, defaultRepository())
        apply(store, save10.code)
        val quoteWithSave10 = store.state.value.quote
        assertEquals(300, quoteWithSave10.discountCents)

        apply(store, rejectedCode)

        val state = store.state.value
        assertEquals(error, state.couponError)
        assertEquals(save10, state.appliedCoupon)
        assertEquals(quoteWithSave10, state.quote)
    }

    @Test
    fun `next successful apply clears the error and replaces the coupon`() = runTest {
        val store = store(backgroundScope, defaultRepository())
        apply(store, save10.code)
        apply(store, "NOPE")

        apply(store, exactlyMin.code)

        assertNull(store.state.value.couponError)
        assertEquals(exactlyMin, store.state.value.appliedCoupon)
        assertEquals(500, store.state.value.quote.discountCents)
    }

    // --- 4. Ignore Apply while validating ------------------------------------

    @Test
    fun `second apply while validating is ignored`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(
            results = mapOf(save10.code to CouponResult.Found(save10), exactlyMin.code to CouponResult.Found(exactlyMin)),
            gates = mapOf(save10.code to gate),
        )
        val store = store(backgroundScope, repository)

        apply(store, save10.code)
        assertTrue(store.state.value.isValidatingCoupon)

        apply(store, exactlyMin.code)
        assertEquals(1, repository.calls.get(), "second lookup must not start")
        assertTrue(store.state.value.isValidatingCoupon)

        gate.complete(Unit)
        runCurrent()

        assertEquals(save10, store.state.value.appliedCoupon)
        assertFalse(store.state.value.isValidatingCoupon)
        assertEquals(1, repository.calls.get())
    }

    @Test
    fun `apply is accepted again once validation finishes`() = runTest {
        val repository = defaultRepository()
        val store = store(backgroundScope, repository)

        apply(store, "OFFLINE")
        apply(store, save10.code)

        assertEquals(2, repository.calls.get())
        assertEquals(save10, store.state.value.appliedCoupon)
        assertNull(store.state.value.couponError)
    }

    @Test
    fun `concurrent applies on a multi-threaded dispatcher start only one lookup`() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeRepository(
            results = mapOf(save10.code to CouponResult.Found(save10)),
            gates = mapOf(save10.code to gate),
        )
        val scope = CoroutineScope(Dispatchers.Default)
        try {
            // Middlewares run in order per intent, so this sees every Apply that got past CouponMiddleware.
            // The one that wins the guard is parked on the gate; the other 199 must fall through.
            val passedCoupon = AtomicInteger(0)
            val counter = Middleware { intent, _, _ ->
                if (intent is CheckoutIntent.ApplyCoupon) passedCoupon.incrementAndGet()
            }
            val store = store(scope, repository, after = listOf(counter))
            repeat(200) { store.dispatch(CheckoutIntent.ApplyCoupon(save10.code)) }

            withTimeout(5_000) { while (passedCoupon.get() < 199) delay(5) }
            assertEquals(1, repository.calls.get())
            assertTrue(store.state.value.isValidatingCoupon)

            gate.complete(Unit)
            withTimeout(5_000) { store.state.first { it.appliedCoupon == save10 && !it.isValidatingCoupon } }
            assertEquals(1, repository.calls.get())
        } finally {
            scope.cancel()
        }
    }
}
