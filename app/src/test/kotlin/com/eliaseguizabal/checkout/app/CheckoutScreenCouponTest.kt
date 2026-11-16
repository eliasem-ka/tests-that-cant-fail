package com.eliaseguizabal.checkout.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CouponError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CheckoutScreenCouponTest {

    @get:Rule
    val compose = createComposeRule()

    private val cart = listOf(CartItem(sku = "Mug", unitPriceCents = 3_000, quantity = 1))
    private val save10 = PercentOff(code = "SAVE10", minSubtotalCents = 2_000, percent = 10, maxDiscountCents = 1_500)

    private fun show(state: CheckoutState, onIntent: (CheckoutIntent) -> Unit = {}) =
        compose.setContent { CheckoutScreen(state = state, onIntent = onIntent) }

    @Test
    fun `shows not found message`() {
        show(CheckoutState(items = cart, couponError = CouponError.NOT_FOUND))
        compose.onNodeWithText("That code doesn't exist.").assertIsDisplayed()
    }

    @Test
    fun `shows below minimum message`() {
        show(CheckoutState(items = cart, couponError = CouponError.BELOW_MINIMUM))
        compose.onNodeWithText("Your subtotal is below this code's minimum.").assertIsDisplayed()
    }

    @Test
    fun `shows network message`() {
        show(CheckoutState(items = cart, couponError = CouponError.NETWORK))
        compose.onNodeWithText("Couldn't check that code. Check your connection and try again.").assertIsDisplayed()
    }

    @Test
    fun `every error has a distinct message`() {
        val messages = CouponError.entries.map { it.message() }
        assertEquals(CouponError.entries.size, messages.toSet().size)
    }

    @Test
    fun `rejection still shows the coupon that remains applied`() {
        show(CheckoutState(items = cart, appliedCoupon = save10, couponError = CouponError.NETWORK))
        compose.onNodeWithText("Couldn't check that code. Check your connection and try again.").assertIsDisplayed()
        compose.onNodeWithText("SAVE10 applied").assertIsDisplayed()
    }

    @Test
    fun `apply sends the typed code`() {
        val intents = mutableListOf<CheckoutIntent>()
        show(CheckoutState(items = cart)) { intents += it }
        compose.onNodeWithText("Coupon code").performTextInput("SAVE10")
        compose.onNodeWithText("Apply").performClick()
        assertEquals(listOf(CheckoutIntent.ApplyCoupon("SAVE10")), intents)
    }
}
