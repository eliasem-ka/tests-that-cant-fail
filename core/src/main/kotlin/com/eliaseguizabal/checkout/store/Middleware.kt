package com.eliaseguizabal.checkout.store

/** Turns an intent into zero or more actions. Ignores intents it doesn't own. */
fun interface Middleware {
    suspend fun handle(
        intent: CheckoutIntent,
        state: () -> CheckoutState,
        dispatch: (CheckoutAction) -> Unit,
    )
}
