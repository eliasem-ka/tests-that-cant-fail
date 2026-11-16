@file:OptIn(ExperimentalCoroutinesApi::class)

package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.Coupon
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.PriceCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val calculator = PriceCalculator()

private fun stateWith(
    items: List<CartItem> = emptyList(),
    appliedCoupon: Coupon? = null,
    isValidatingCoupon: Boolean = false,
) = CheckoutState(
    items = items,
    appliedCoupon = appliedCoupon,
    quote = calculator.quote(items, appliedCoupon, 0),
    isValidatingCoupon = isValidatingCoupon,
)

private fun cartOf(vararg items: CartItem) = items.toList()

private fun line(sku: String, price: Long, quantity: Int = 1) =
    CartItem(sku = sku, unitPriceCents = price, quantity = quantity)

// ---------------------------------------------------------------------------
// CartMiddleware
// ---------------------------------------------------------------------------

class CartMiddlewareTest {

    private val middleware = CartMiddleware()

    private suspend fun dispatchedFor(intent: CheckoutIntent, state: CheckoutState): List<CheckoutAction> {
        val actions = mutableListOf<CheckoutAction>()
        middleware.handle(intent, { state }, { actions += it })
        return actions
    }

    @Test
    fun `AddItem appends a new sku`() = runTest {
        val existing = line("A", 100)
        val actions = dispatchedFor(
            CheckoutIntent.AddItem(line("B", 200)),
            stateWith(items = cartOf(existing)),
        )
        assertEquals(listOf(CheckoutAction.ItemsChanged(listOf(existing, line("B", 200)))), actions)
    }

    @Test
    fun `AddItem adds quantity to an existing sku instead of duplicating the line`() = runTest {
        val actions = dispatchedFor(
            CheckoutIntent.AddItem(line("A", 100, quantity = 2)),
            stateWith(items = cartOf(line("A", 100, quantity = 1))),
        )
        assertEquals(listOf(CheckoutAction.ItemsChanged(listOf(line("A", 100, quantity = 3)))), actions)
    }

    @Test
    fun `AddItem into an empty cart creates the first line`() = runTest {
        val actions = dispatchedFor(CheckoutIntent.AddItem(line("A", 100)), stateWith())
        assertEquals(listOf(CheckoutAction.ItemsChanged(listOf(line("A", 100)))), actions)
    }

    @Test
    fun `RemoveItem drops the matching sku only`() = runTest {
        val actions = dispatchedFor(
            CheckoutIntent.RemoveItem("A"),
            stateWith(items = cartOf(line("A", 100), line("B", 200))),
        )
        assertEquals(listOf(CheckoutAction.ItemsChanged(listOf(line("B", 200)))), actions)
    }

    @Test
    fun `RemoveItem for an unknown sku dispatches nothing`() = runTest {
        val actions = dispatchedFor(
            CheckoutIntent.RemoveItem("ZZZ"),
            stateWith(items = cartOf(line("A", 100))),
        )
        assertTrue(actions.isEmpty())
    }

    @Test
    fun `UsePoints dispatches PointsChanged`() = runTest {
        val actions = dispatchedFor(CheckoutIntent.UsePoints(750), stateWith())
        assertEquals(listOf(CheckoutAction.PointsChanged(750)), actions)
    }

    @Test
    fun `intents it does not own are ignored`() = runTest {
        assertTrue(dispatchedFor(CheckoutIntent.ApplyCoupon("SAVE10"), stateWith()).isEmpty())
        assertTrue(dispatchedFor(CheckoutIntent.SaveCart, stateWith()).isEmpty())
    }
}

// ---------------------------------------------------------------------------
// CouponMiddleware
// ---------------------------------------------------------------------------

class CouponMiddlewareTest {

    private val fiveOff = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)
    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)

    private suspend fun actionsFor(
        repo: FakeCouponRepository,
        state: CheckoutState,
        intent: CheckoutIntent = CheckoutIntent.ApplyCoupon("ANY"),
    ): List<CheckoutAction> {
        val actions = mutableListOf<CheckoutAction>()
        CouponMiddleware(repo, calculator).handle(intent, { state }, { actions += it })
        return actions
    }

    @Test
    fun `applies an eligible coupon after announcing validation`() = runTest {
        val repo = FakeCouponRepository(CouponResult.Found(fiveOff))
        val actions = actionsFor(repo, stateWith(items = cartOf(line("A", 1_000))))

        assertEquals(
            listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponApplied(fiveOff)),
            actions,
        )
    }

    @Test
    fun `passes the code to the repository as typed`() = runTest {
        val repo = FakeCouponRepository(CouponResult.NotFound)
        actionsFor(repo, stateWith(), CheckoutIntent.ApplyCoupon(" save10 "))

        assertEquals(listOf(" save10 "), repo.requestedCodes)
    }

    @Test
    fun `rejects a found coupon whose minimum the cart does not meet`() = runTest {
        val repo = FakeCouponRepository(CouponResult.Found(save10))
        // subtotal 1999 is one cent under the 2000 minimum
        val actions = actionsFor(repo, stateWith(items = cartOf(line("A", 1_999))))

        assertEquals(
            listOf(
                CheckoutAction.CouponValidationStarted,
                CheckoutAction.CouponRejected(CouponError.BELOW_MINIMUM),
            ),
            actions,
        )
    }

    @Test
    fun `applies a coupon when the subtotal exactly meets its minimum`() = runTest {
        val repo = FakeCouponRepository(CouponResult.Found(save10))
        val actions = actionsFor(repo, stateWith(items = cartOf(line("A", 2_000))))

        assertEquals(CheckoutAction.CouponApplied(save10), actions.last())
    }

    @Test
    fun `rejects an unknown code as NOT_FOUND`() = runTest {
        val actions = actionsFor(FakeCouponRepository(CouponResult.NotFound), stateWith())

        assertEquals(
            listOf(
                CheckoutAction.CouponValidationStarted,
                CheckoutAction.CouponRejected(CouponError.NOT_FOUND),
            ),
            actions,
        )
    }

    @Test
    fun `rejects a network failure as NETWORK`() = runTest {
        val actions = actionsFor(FakeCouponRepository(CouponResult.NetworkError), stateWith())

        assertEquals(
            listOf(
                CheckoutAction.CouponValidationStarted,
                CheckoutAction.CouponRejected(CouponError.NETWORK),
            ),
            actions,
        )
    }

    @Test
    fun `ignores intents other than ApplyCoupon without touching the repository`() = runTest {
        val repo = FakeCouponRepository(CouponResult.Found(fiveOff))
        val actions = actionsFor(repo, stateWith(), CheckoutIntent.SaveCart)

        assertTrue(actions.isEmpty())
        assertTrue(repo.requestedCodes.isEmpty())
    }

    @Test
    fun `ignores a new request while a validation is already in flight`() = runTest {
        val repo = FakeCouponRepository(CouponResult.Found(fiveOff))
        val actions = actionsFor(repo, stateWith(isValidatingCoupon = true))

        assertTrue(actions.isEmpty())
        assertTrue(repo.requestedCodes.isEmpty())
    }
}

// ---------------------------------------------------------------------------
// CartPersistenceMiddleware
// ---------------------------------------------------------------------------

class CartPersistenceMiddlewareTest {

    @Test
    fun `SaveCart clears the cached quote before saving a snapshot and dispatches CartSaved`() = runTest {
        val store = RecordingCartStore()
        val items = cartOf(line("A", 100, quantity = 2))
        val actions = mutableListOf<CheckoutAction>()

        CartPersistenceMiddleware(store).handle(
            CheckoutIntent.SaveCart,
            { stateWith(items = items, appliedCoupon = AmountOff("FIVEOFF", 0, 500)) },
            { actions += it },
        )

        assertEquals(listOf("clearCachedQuote", "save"), store.calls)
        assertEquals(listOf(CartSnapshot(items, "FIVEOFF")), store.saved)
        assertEquals(listOf<CheckoutAction>(CheckoutAction.CartSaved), actions)
    }

    @Test
    fun `snapshot has a null coupon code when no coupon is applied`() = runTest {
        val store = RecordingCartStore()
        val items = cartOf(line("A", 100))

        CartPersistenceMiddleware(store).handle(
            CheckoutIntent.SaveCart,
            { stateWith(items = items) },
            { },
        )

        assertEquals(listOf(CartSnapshot(items, null)), store.saved)
    }

    @Test
    fun `other intents neither save nor dispatch`() = runTest {
        val store = RecordingCartStore()
        val actions = mutableListOf<CheckoutAction>()

        CartPersistenceMiddleware(store).handle(
            CheckoutIntent.AddItem(line("A", 100)),
            { stateWith() },
            { actions += it },
        )

        assertTrue(store.calls.isEmpty())
        assertTrue(actions.isEmpty())
    }
}
