package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.CartStore
import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.domain.Quote
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiddlewareTest {
    private val actions = mutableListOf<CheckoutAction>()
    private val dispatch: (CheckoutAction) -> Unit = { actions += it }
    private val a = CartItem("a", 100, 1)
    private val b = CartItem("b", 200, 2)

    // --- CartMiddleware
    @Test fun `AddItem appends new sku`() = runTest {
        CartMiddleware().handle(CheckoutIntent.AddItem(b), { CheckoutState(items = listOf(a)) }, dispatch)
        assertEquals(listOf<CheckoutAction>(CheckoutAction.ItemsChanged(listOf(a, b))), actions)
    }

    @Test fun `AddItem merges quantity for existing sku`() = runTest {
        CartMiddleware().handle(CheckoutIntent.AddItem(a.copy(quantity = 3)), { CheckoutState(items = listOf(a, b)) }, dispatch)
        assertEquals(
            listOf<CheckoutAction>(CheckoutAction.ItemsChanged(listOf(a.copy(quantity = 4), b))),
            actions,
        )
    }

    @Test fun `RemoveItem removes sku`() = runTest {
        CartMiddleware().handle(CheckoutIntent.RemoveItem("a"), { CheckoutState(items = listOf(a, b)) }, dispatch)
        assertEquals(listOf<CheckoutAction>(CheckoutAction.ItemsChanged(listOf(b))), actions)
    }

    @Test fun `RemoveItem unknown sku dispatches nothing`() = runTest {
        CartMiddleware().handle(CheckoutIntent.RemoveItem("zzz"), { CheckoutState(items = listOf(a)) }, dispatch)
        assertTrue(actions.isEmpty())
    }

    @Test fun `UsePoints dispatches PointsChanged`() = runTest {
        CartMiddleware().handle(CheckoutIntent.UsePoints(250), { CheckoutState() }, dispatch)
        assertEquals(listOf<CheckoutAction>(CheckoutAction.PointsChanged(250)), actions)
    }

    @Test fun `CartMiddleware ignores other intents`() = runTest {
        CartMiddleware().handle(CheckoutIntent.SaveCart, { CheckoutState() }, dispatch)
        CartMiddleware().handle(CheckoutIntent.ApplyCoupon("X"), { CheckoutState() }, dispatch)
        assertTrue(actions.isEmpty())
    }

    // --- CouponMiddleware
    private val coupon = AmountOff("FIVEOFF", 1_000, 500)

    /** Fake repository: answers per code and records every code it was asked about. */
    private class FakeCouponRepository(private val byCode: Map<String, CouponResult>) : CouponRepository {
        val asked = mutableListOf<String>()
        override suspend fun find(code: String): CouponResult {
            asked += code
            return byCode[code] ?: CouponResult.NotFound
        }
    }
    private fun couponMw(result: CouponResult, code: String = "X"): Pair<CouponMiddleware, FakeCouponRepository> {
        val repo = FakeCouponRepository(mapOf(code to result))
        return CouponMiddleware(repo, PriceCalculator()) to repo
    }
    private fun stateWithSubtotal(sub: Long) = CheckoutState(quote = Quote.EMPTY.copy(subtotalCents = sub))

    @Test fun `eligible coupon is applied`() = runTest {
        val (mw, _) = couponMw(CouponResult.Found(coupon), "FIVEOFF")
        mw.handle(CheckoutIntent.ApplyCoupon("FIVEOFF"), { stateWithSubtotal(1_000) }, dispatch)
        assertEquals(listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponApplied(coupon)), actions)
    }

    @Test fun `coupon below minimum is rejected`() = runTest {
        val (mw, _) = couponMw(CouponResult.Found(coupon), "FIVEOFF")
        mw.handle(CheckoutIntent.ApplyCoupon("FIVEOFF"), { stateWithSubtotal(999) }, dispatch)
        assertEquals(
            listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponRejected(CouponError.BELOW_MINIMUM)),
            actions,
        )
    }

    @Test fun `the typed code is what gets looked up`() = runTest {
        // The fake only knows "FIVEOFF"; looking up anything else yields NotFound, not Applied.
        val (mw, repo) = couponMw(CouponResult.Found(coupon), "FIVEOFF")
        mw.handle(CheckoutIntent.ApplyCoupon("FIVEOFF"), { stateWithSubtotal(1_000) }, dispatch)
        assertEquals(listOf("FIVEOFF"), repo.asked)
        assertEquals(CheckoutAction.CouponApplied(coupon), actions.last())
    }

    @Test fun `not found is rejected`() = runTest {
        val (mw, _) = couponMw(CouponResult.NotFound)
        mw.handle(CheckoutIntent.ApplyCoupon("X"), { CheckoutState() }, dispatch)
        assertEquals(
            listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponRejected(CouponError.NOT_FOUND)),
            actions,
        )
    }

    @Test fun `network error is rejected`() = runTest {
        val (mw, _) = couponMw(CouponResult.NetworkError)
        mw.handle(CheckoutIntent.ApplyCoupon("X"), { CheckoutState() }, dispatch)
        assertEquals(
            listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponRejected(CouponError.NETWORK)),
            actions,
        )
    }

    @Test fun `apply while validating is ignored`() = runTest {
        val (mw, repo) = couponMw(CouponResult.NotFound)
        mw.handle(CheckoutIntent.ApplyCoupon("X"), { CheckoutState(isValidatingCoupon = true) }, dispatch)
        assertTrue(actions.isEmpty())
        assertTrue(repo.asked.isEmpty())
    }

    @Test fun `coupon middleware ignores other intents`() = runTest {
        val (mw, repo) = couponMw(CouponResult.NotFound)
        listOf(
            CheckoutIntent.SaveCart, CheckoutIntent.AddItem(a), CheckoutIntent.RemoveItem("a"), CheckoutIntent.UsePoints(5),
        ).forEach { mw.handle(it, { CheckoutState() }, dispatch) }
        assertTrue(actions.isEmpty())
        assertTrue(repo.asked.isEmpty())
    }

    // --- CartPersistenceMiddleware
    /** Models a store that still holds a stale cached quote until it is cleared. */
    private class FakeCartStore : CartStore {
        var cachedQuote: Quote? = Quote(1, 1, 1, 1, 1, 1)
        var saved: CartSnapshot? = null
        var cachedQuoteWhenSaved: Quote? = Quote.EMPTY // sentinel: overwritten by save()
        override suspend fun clearCachedQuote() { cachedQuote = null }
        override suspend fun save(snapshot: CartSnapshot) {
            cachedQuoteWhenSaved = cachedQuote
            saved = snapshot
        }
    }

    @Test fun `SaveCart drops stale quote before saving the snapshot and dispatches CartSaved`() = runTest {
        val store = FakeCartStore()
        val state = CheckoutState(items = listOf(a), appliedCoupon = coupon)
        CartPersistenceMiddleware(store).handle(CheckoutIntent.SaveCart, { state }, dispatch)
        assertEquals(CartSnapshot(listOf(a), "FIVEOFF"), store.saved)
        assertNull(store.cachedQuote)
        assertNull(store.cachedQuoteWhenSaved, "the stale quote must be gone by the time the snapshot is saved")
        assertEquals(listOf<CheckoutAction>(CheckoutAction.CartSaved), actions)
    }

    @Test fun `SaveCart without coupon saves null code`() = runTest {
        val store = FakeCartStore()
        CartPersistenceMiddleware(store).handle(CheckoutIntent.SaveCart, { CheckoutState(items = listOf(a)) }, dispatch)
        assertEquals(CartSnapshot(listOf(a), null), store.saved)
    }

    @Test fun `persistence ignores other intents`() = runTest {
        val store = FakeCartStore()
        listOf(
            CheckoutIntent.UsePoints(1), CheckoutIntent.AddItem(a), CheckoutIntent.RemoveItem("a"), CheckoutIntent.ApplyCoupon("X"),
        ).forEach { CartPersistenceMiddleware(store).handle(it, { CheckoutState(items = listOf(a)) }, dispatch) }
        assertTrue(actions.isEmpty())
        assertNull(store.saved)
        assertEquals(Quote(1, 1, 1, 1, 1, 1), store.cachedQuote)
    }
}
