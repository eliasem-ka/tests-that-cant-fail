package com.eliaseguizabal.checkout.app

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.eliaseguizabal.checkout.data.CouponResult
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CartMiddleware
import com.eliaseguizabal.checkout.store.CheckoutAction
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutReducer
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CheckoutStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

    private lateinit var main: TestDispatcher

    @Before
    fun setUp() {
        main = StandardTestDispatcher()
        Dispatchers.setMain(main)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun factoryViewModel(): CheckoutViewModel =
        CheckoutViewModel.Factory.create(CheckoutViewModel::class.java, CreationExtras.Empty)

    @Test
    fun `onIntent reaches the store and changes the state`() = runTest(main) {
        val item = CartItem(sku = "Mug", unitPriceCents = 1_000, quantity = 2)
        val viewModel = CheckoutViewModel { scope ->
            val reducer = CheckoutReducer(PriceCalculator())
            CheckoutStore(CheckoutState(), reducer, listOf(CartMiddleware()), scope)
        }

        viewModel.onIntent(CheckoutIntent.AddItem(item))
        advanceUntilIdle()

        assertEquals(listOf(item), viewModel.state.value.items)
        assertEquals(2_000, viewModel.state.value.quote.subtotalCents)
    }

    @Test
    fun `app starts with the demo cart priced`() = runTest(main) {
        val viewModel = factoryViewModel()
        advanceUntilIdle()

        val state = viewModel.state.value
        assertEquals(listOf("Wireless headphones", "USB-C cable", "Phone case"), state.items.map { it.sku })
        // 39.99 + 2 x 8.99 + 14.99 = 72.96; tax 9.48; free shipping
        assertEquals(7_296, state.quote.subtotalCents)
        assertEquals(948, state.quote.taxCents)
        assertEquals(0, state.quote.shippingCents)
        assertEquals(8_244, state.quote.totalCents)
    }

    @Test
    fun `app wires coupon lookup into checkout`() = runTest(main) {
        val viewModel = factoryViewModel()
        advanceUntilIdle()

        viewModel.onIntent(CheckoutIntent.ApplyCoupon("save10"))
        advanceUntilIdle()

        // 10% of 72.96 = 7.29
        assertEquals("SAVE10", viewModel.state.value.appliedCoupon?.code)
        assertEquals(729, viewModel.state.value.quote.discountCents)
    }
}
