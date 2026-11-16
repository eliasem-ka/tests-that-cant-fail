package com.eliaseguizabal.checkout.domain

import com.eliaseguizabal.checkout.domain.PricingRules.FREE_SHIPPING_ABOVE_CENTS
import com.eliaseguizabal.checkout.domain.PricingRules.SHIPPING_FEE_CENTS
import com.eliaseguizabal.checkout.domain.PricingRules.TAX_RATE_PERCENT

class PriceCalculator {

    fun isEligible(coupon: Coupon, subtotalCents: Long): Boolean =
        subtotalCents >= coupon.minSubtotalCents

    fun quote(items: List<CartItem>, coupon: Coupon?, loyaltyPointsCents: Long): Quote {
        if (items.isEmpty()) return Quote.EMPTY

        val subtotal = items.sumOf { it.unitPriceCents * it.quantity }
        val discount = discountFor(coupon, subtotal)
        val discounted = subtotal - discount
        val tax = (discounted * TAX_RATE_PERCENT + 50) / 100
        val shipping = if (discounted > FREE_SHIPPING_ABOVE_CENTS) 0L else SHIPPING_FEE_CENTS
        val beforePoints = discounted + tax + shipping
        val points = minOf(loyaltyPointsCents.coerceAtLeast(0), beforePoints)

        return Quote(
            subtotalCents = subtotal,
            discountCents = discount,
            taxCents = tax,
            shippingCents = shipping,
            pointsAppliedCents = points,
            totalCents = beforePoints - points,
        )
    }

    private fun discountFor(coupon: Coupon?, subtotal: Long): Long {
        if (coupon == null || !isEligible(coupon, subtotal)) return 0
        return when (coupon) {
            is PercentOff -> minOf(subtotal * coupon.percent / 100, coupon.maxDiscountCents)
            is AmountOff -> minOf(coupon.amountCents, subtotal)
        }
    }
}
