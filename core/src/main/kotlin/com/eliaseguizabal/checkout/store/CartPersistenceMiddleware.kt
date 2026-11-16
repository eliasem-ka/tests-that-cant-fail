package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.data.CartSnapshot
import com.eliaseguizabal.checkout.data.CartStore

class CartPersistenceMiddleware(private val cartStore: CartStore) : Middleware {

    override suspend fun handle(
        intent: CheckoutIntent,
        state: () -> CheckoutState,
        dispatch: (CheckoutAction) -> Unit,
    ) {
        if (intent != CheckoutIntent.SaveCart) return
        val current = state()
        cartStore.clearCachedQuote()
        cartStore.save(CartSnapshot(current.items, current.appliedCoupon?.code))
        dispatch(CheckoutAction.CartSaved)
    }
}
