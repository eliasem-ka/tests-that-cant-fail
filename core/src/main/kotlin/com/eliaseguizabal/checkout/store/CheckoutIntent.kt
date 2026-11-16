package com.eliaseguizabal.checkout.store

import com.eliaseguizabal.checkout.domain.CartItem

/** What the user asks for. Handled by middlewares. */
sealed interface CheckoutIntent {
    data class AddItem(val item: CartItem) : CheckoutIntent
    data class RemoveItem(val sku: String) : CheckoutIntent
    data class ApplyCoupon(val code: String) : CheckoutIntent
    data class UsePoints(val cents: Long) : CheckoutIntent
    data object SaveCart : CheckoutIntent
}
