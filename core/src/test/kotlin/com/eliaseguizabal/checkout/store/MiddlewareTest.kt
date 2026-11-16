package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.CartStore
import com.eliaseguizabal.checkout.data.CouponRepository
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.domain.Quote
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.coJustRun
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
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
    private fun couponMw(result: CouponResult): Pair<CouponMiddleware, CouponRepository> {
        val repo = mockk<CouponRepository>()
        coEvery { repo.find(any()) } returns result
        return CouponMiddleware(repo, PriceCalculator()) to repo
    }
    private fun stateWithSubtotal(sub: Long) = CheckoutState(quote = Quote.EMPTY.copy(subtotalCents = sub))

    @Test fun `eligible coupon is applied`() = runTest {
        val (mw, _) = couponMw(CouponResult.Found(coupon))
        mw.handle(CheckoutIntent.ApplyCoupon("FIVEOFF"), { stateWithSubtotal(1_000) }, dispatch)
        assertEquals(listOf(CheckoutAction.CouponValidationStarted, CheckoutAction.CouponApplied(coupon)), actions)
    }

    @Test fun `coupon below minimum is rejected`() = runTest {
        val (mw, _) = couponMw(CouponResult.Found(coupon))
        mw.handle(CheckoutIntent.ApplyCoupon("FIVEOFF"), { stateWithSubtotal(999) }, dispatch)
        assertEquals(CheckoutAction.CouponRejected(CouponError.BELOW_MINIMUM), actions.last())
    }

    @Test fun `not found is rejected`() = runTest {
        val (mw, _) = couponMw(CouponResult.NotFound)
        mw.handle(CheckoutIntent.ApplyCoupon("X"), { CheckoutState() }, dispatch)
        assertEquals(CheckoutAction.CouponRejected(CouponError.NOT_FOUND), actions.last())
    }

    @Test fun `network error is rejected`() = runTest {
        val (mw, _) = couponMw(CouponResult.NetworkError)
        mw.handle(CheckoutIntent.ApplyCoupon("X"), { CheckoutState() }, dispatch)
        assertEquals(CheckoutAction.CouponRejected(CouponError.NETWORK), actions.last())
    }

    @Test fun `apply while validating is ignored`() = runTest {
        val (mw, repo) = couponMw(CouponResult.NotFound)
        mw.handle(CheckoutIntent.ApplyCoupon("X"), { CheckoutState(isValidatingCoupon = true) }, dispatch)
        assertTrue(actions.isEmpty())
        coVerify(exactly = 0) { repo.find(any()) }
    }

    @Test fun `coupon middleware ignores other intents`() = runTest {
        val (mw, repo) = couponMw(CouponResult.NotFound)
        mw.handle(CheckoutIntent.SaveCart, { CheckoutState() }, dispatch)
        assertTrue(actions.isEmpty())
        coVerify(exactly = 0) { repo.find(any()) }
    }

    // --- CartPersistenceMiddleware
    @Test fun `SaveCart clears cached quote, saves snapshot, dispatches CartSaved`() = runTest {
        val store = mockk<CartStore>()
        coJustRun { store.clearCachedQuote() }
        coJustRun { store.save(any()) }
        val state = CheckoutState(items = listOf(a), appliedCoupon = coupon)
        CartPersistenceMiddleware(store).handle(CheckoutIntent.SaveCart, { state }, dispatch)
        coVerifyOrder {
            store.clearCachedQuote()
            store.save(CartSnapshot(listOf(a), "FIVEOFF"))
        }
        assertEquals(listOf<CheckoutAction>(CheckoutAction.CartSaved), actions)
    }

    @Test fun `SaveCart without coupon saves null code`() = runTest {
        val store = mockk<CartStore>()
        coJustRun { store.clearCachedQuote() }
        coJustRun { store.save(any()) }
        CartPersistenceMiddleware(store).handle(CheckoutIntent.SaveCart, { CheckoutState(items = listOf(a)) }, dispatch)
        coVerify { store.save(CartSnapshot(listOf(a), null)) }
    }

    @Test fun `persistence ignores other intents`() = runTest {
        val store = mockk<CartStore>()
        CartPersistenceMiddleware(store).handle(CheckoutIntent.UsePoints(1), { CheckoutState() }, dispatch)
        assertTrue(actions.isEmpty())
    }
}
