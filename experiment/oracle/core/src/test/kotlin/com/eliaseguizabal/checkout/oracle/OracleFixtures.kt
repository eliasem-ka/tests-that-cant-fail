package com.eliaseguizabal.checkout.oracle

import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PercentOff

val SAVE10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)
val FIVEOFF = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)

fun item(priceCents: Long, quantity: Int = 1, sku: String = "A") =
    CartItem(sku = sku, unitPriceCents = priceCents, quantity = quantity)
