package com.eliaseguizabal.checkout.store

class CartMiddleware : Middleware {

    override suspend fun handle(
        intent: CheckoutIntent,
        state: () -> CheckoutState,
        dispatch: (CheckoutAction) -> Unit,
    ) {
        when (intent) {
            is CheckoutIntent.AddItem -> {
                val items = state().items
                val existing = items.find { it.sku == intent.item.sku }
                val updated = if (existing == null) {
                    items + intent.item
                } else {
                    items.map {
                        if (it.sku == existing.sku) it.copy(quantity = it.quantity + intent.item.quantity) else it
                    }
                }
                dispatch(CheckoutAction.ItemsChanged(updated))
            }
            is CheckoutIntent.RemoveItem -> {
                val items = state().items
                if (items.none { it.sku == intent.sku }) return
                dispatch(CheckoutAction.ItemsChanged(items.filterNot { it.sku == intent.sku }))
            }
            is CheckoutIntent.UsePoints -> dispatch(CheckoutAction.PointsChanged(intent.cents))
            else -> Unit
        }
    }
}
