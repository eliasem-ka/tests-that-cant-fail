package com.eliaseguizabal.checkout.domain

data class Quote(
    val subtotalCents: Long,
    val discountCents: Long,
    val taxCents: Long,
    val shippingCents: Long,
    val pointsAppliedCents: Long,
    val totalCents: Long,
) {
    companion object {
        val EMPTY = Quote(0, 0, 0, 0, 0, 0)
    }
}
