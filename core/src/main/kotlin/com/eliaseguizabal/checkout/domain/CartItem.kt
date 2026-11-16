package com.eliaseguizabal.checkout.domain

data class CartItem(
    val sku: String,
    val unitPriceCents: Long,
    val quantity: Int,
)
