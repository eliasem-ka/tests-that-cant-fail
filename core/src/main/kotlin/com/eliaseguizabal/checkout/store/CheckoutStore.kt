package com.eliaseguizabal.checkout.store

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CheckoutStore(
    initial: CheckoutState,
    private val reducer: CheckoutReducer,
    private val middlewares: List<Middleware>,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(initial)
    val state: StateFlow<CheckoutState> = _state.asStateFlow()

    fun dispatch(intent: CheckoutIntent) {
        scope.launch {
            middlewares.forEach { middleware ->
                middleware.handle(intent, { _state.value }, ::reduce)
            }
        }
    }

    private fun reduce(action: CheckoutAction) {
        _state.update { reducer.reduce(it, action) }
    }
}
