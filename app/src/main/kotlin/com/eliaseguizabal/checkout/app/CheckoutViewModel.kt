package com.eliaseguizabal.checkout.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.eliaseguizabal.checkout.data.InMemoryCartStore
import com.eliaseguizabal.checkout.data.InMemoryCouponRepository
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CartMiddleware
import com.eliaseguizabal.checkout.store.CartPersistenceMiddleware
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutReducer
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CheckoutStore
import com.eliaseguizabal.checkout.store.CouponMiddleware
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class CheckoutViewModel(storeFactory: (CoroutineScope) -> CheckoutStore) : ViewModel() {

    private val store = storeFactory(viewModelScope)
    val state: StateFlow<CheckoutState> = store.state

    fun onIntent(intent: CheckoutIntent) = store.dispatch(intent)

    companion object {
        private val demoCart = listOf(
            CartItem(sku = "Wireless headphones", unitPriceCents = 3_999, quantity = 1),
            CartItem(sku = "USB-C cable", unitPriceCents = 899, quantity = 2),
            CartItem(sku = "Phone case", unitPriceCents = 1_499, quantity = 1),
        )

        val Factory = viewModelFactory {
            initializer {
                CheckoutViewModel { scope ->
                    val calculator = PriceCalculator()
                    CheckoutStore(
                        initial = CheckoutState(),
                        reducer = CheckoutReducer(calculator),
                        middlewares = listOf(
                            CartMiddleware(),
                            CouponMiddleware(InMemoryCouponRepository(), calculator),
                            CartPersistenceMiddleware(InMemoryCartStore()),
                        ),
                        scope = scope,
                    ).also { store -> demoCart.forEach { store.dispatch(CheckoutIntent.AddItem(it)) } }
                }
            }
        }
    }
}
