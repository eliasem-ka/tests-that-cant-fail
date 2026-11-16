package com.eliaseguizabal.checkout.domain

sealed interface Coupon {
    val code: String
    val minSubtotalCents: Long
}

/** [percent]% off the subtotal, never more than [maxDiscountCents]. */
data class PercentOff(
    override val code: String,
    override val minSubtotalCents: Long,
    val percent: Int,
    val maxDiscountCents: Long,
) : Coupon

/** A fixed amount off the subtotal. */
data class AmountOff(
    override val code: String,
    override val minSubtotalCents: Long,
    val amountCents: Long,
) : Coupon
