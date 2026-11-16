package com.eliaseguizabal.checkout.app

import androidx.lifecycle.viewmodel.CreationExtras
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CartMiddleware
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutReducer
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CheckoutStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CheckoutViewModelTest {

    // ViewModelScope runs on Dispatchers.Main; unconfined makes dispatched intents
    // complete before the call returns, so state can be asserted directly.
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModelWithEmptyCart(): CheckoutViewModel {
        val calculator = PriceCalculator()
        return CheckoutViewModel { scope ->
            CheckoutStore(
                initial = CheckoutState(),
                reducer = CheckoutReducer(calculator),
                middlewares = listOf(CartMiddleware()),
                scope = scope,
            )
        }
    }

    @Test
    fun `factory seeds the demo cart with three lines`() {
        val viewModel = CheckoutViewModel.Factory.create(CheckoutViewModel::class.java, CreationExtras.Empty)

        assertEquals(3, viewModel.state.value.items.size)
    }

    @Test
    fun `factory quotes the demo cart subtotal`() {
        val viewModel = CheckoutViewModel.Factory.create(CheckoutViewModel::class.java, CreationExtras.Empty)

        // 3999 + 2 * 899 + 1499
        assertEquals(7_296, viewModel.state.value.quote.subtotalCents)
    }

    @Test
    fun `onIntent forwards intents to the store`() {
        val viewModel = viewModelWithEmptyCart()

        viewModel.onIntent(CheckoutIntent.AddItem(CartItem("Mug", 1_200, 1)))

        assertEquals(listOf(CartItem("Mug", 1_200, 1)), viewModel.state.value.items)
    }

    @Test
    fun `state reflects removals requested through onIntent`() {
        val viewModel = viewModelWithEmptyCart()
        viewModel.onIntent(CheckoutIntent.AddItem(CartItem("Mug", 1_200, 1)))
        viewModel.onIntent(CheckoutIntent.AddItem(CartItem("Pen", 300, 1)))

        viewModel.onIntent(CheckoutIntent.RemoveItem("Mug"))

        assertEquals(listOf(CartItem("Pen", 300, 1)), viewModel.state.value.items)
    }

    @Test
    fun `initial state of a fresh view model is empty`() {
        assertEquals(CheckoutState(), viewModelWithEmptyCart().state.value)
    }
}
