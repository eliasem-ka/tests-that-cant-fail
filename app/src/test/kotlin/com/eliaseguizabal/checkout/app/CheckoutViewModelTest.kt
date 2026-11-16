package com.eliaseguizabal.checkout.app

import androidx.lifecycle.viewmodel.CreationExtras
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutReducer
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CheckoutStore
import com.eliaseguizabal.checkout.store.CouponError
import com.eliaseguizabal.checkout.store.Middleware
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

    private val main: TestDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(main)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `onIntent forwards to the store built on the view model scope`() {
        val received = mutableListOf<CheckoutIntent>()
        val viewModel = CheckoutViewModel { scope ->
            CheckoutStore(
                initial = CheckoutState(),
                reducer = CheckoutReducer(PriceCalculator()),
                middlewares = listOf(Middleware { intent, _, _ -> received += intent }),
                scope = scope,
            )
        }

        viewModel.onIntent(CheckoutIntent.UsePoints(5))
        viewModel.onIntent(CheckoutIntent.SaveCart)
        main.scheduler.advanceUntilIdle()

        assertEquals(listOf(CheckoutIntent.UsePoints(5), CheckoutIntent.SaveCart), received)
    }

    @Test
    fun `state is the store's state`() {
        val store = CheckoutStore(CheckoutState(), CheckoutReducer(PriceCalculator()), emptyList(), TestScope(main))
        val viewModel = CheckoutViewModel { store }

        assertTrue(viewModel.state === store.state)
    }

    @Test
    fun `factory builds a view model preloaded with the demo cart`() {
        val viewModel = CheckoutViewModel.Factory.create(CheckoutViewModel::class.java, CreationExtras.Empty)
        main.scheduler.advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(
            listOf(
                CartItem("Wireless headphones", 3_999, 1),
                CartItem("USB-C cable", 899, 2),
                CartItem("Phone case", 1_499, 1),
            ),
            state.items,
        )
        // 3999 + 1798 + 1499 = 7296; tax 948; free shipping
        assertEquals(7_296, state.quote.subtotalCents)
        assertEquals(0, state.quote.shippingCents)
        assertEquals(8_244, state.quote.totalCents)
    }

    @Test
    fun `factory wiring validates coupons through the real repository and calculator`() {
        val viewModel = CheckoutViewModel.Factory.create(CheckoutViewModel::class.java, CreationExtras.Empty)
        main.scheduler.advanceUntilIdle()

        viewModel.onIntent(CheckoutIntent.ApplyCoupon("big20"))
        main.scheduler.advanceUntilIdle()

        // BIG20 needs 10000; demo cart is 7296
        assertEquals(CouponError.BELOW_MINIMUM, viewModel.state.value.couponError)

        viewModel.onIntent(CheckoutIntent.ApplyCoupon("SAVE10"))
        main.scheduler.advanceUntilIdle()

        // 10% of 7296 = 729 (cap 1500)
        assertEquals(729, viewModel.state.value.quote.discountCents)
    }
}
