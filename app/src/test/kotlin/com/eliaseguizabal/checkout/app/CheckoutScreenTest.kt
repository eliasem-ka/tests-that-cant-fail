package com.eliaseguizabal.checkout.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CouponError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Tall screen so every LazyColumn item is composed without scrolling. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class CheckoutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val intents = mutableListOf<CheckoutIntent>()
    private val calculator = PriceCalculator()
    private val items = listOf(CartItem("Phone case", 1_499, 1), CartItem("USB-C cable", 899, 2))

    private fun stateOf(
        items: List<CartItem> = this.items,
        coupon: AmountOff? = null,
        error: CouponError? = null,
        validating: Boolean = false,
    ) = CheckoutState(
        items = items,
        appliedCoupon = coupon,
        quote = calculator.quote(items, coupon, 0),
        couponError = error,
        isValidatingCoupon = validating,
    )

    private fun show(state: CheckoutState) =
        compose.setContent { MaterialTheme { CheckoutScreen(state = state, onIntent = { intents += it }) } }

    private fun couponField() = compose.onNode(hasSetTextAction() and hasText("Coupon code"))
    private fun pointsField() = compose.onNode(hasSetTextAction() and hasText("Loyalty points (cents)"))
    private fun button(label: String) = compose.onNode(hasClickAction() and hasText(label))

    @Test
    fun `lists each cart line with quantity, unit price and line total`() {
        show(stateOf())

        compose.onNodeWithText("Phone case").assertIsDisplayed()
        compose.onNodeWithText("1 × $14.99").assertIsDisplayed()
        compose.onNodeWithText("USB-C cable").assertIsDisplayed()
        compose.onNodeWithText("2 × $8.99").assertIsDisplayed()
        compose.onNodeWithText("$17.98").assertIsDisplayed()
    }

    @Test
    fun `Remove dispatches RemoveItem for that row's sku`() {
        show(stateOf())

        compose.onAllNodesWithText("Remove")[1].performClick()

        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.RemoveItem("USB-C cable")), intents)
    }

    @Test
    fun `Apply is disabled until a code is typed, then dispatches it`() {
        show(stateOf())

        button("Apply").assertIsNotEnabled()
        couponField().performTextInput("save10")
        button("Apply").assertIsEnabled().performClick()

        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.ApplyCoupon("save10")), intents)
    }

    @Test
    fun `whitespace-only code keeps Apply disabled`() {
        show(stateOf())

        couponField().performTextInput("   ")
        button("Apply").assertIsNotEnabled()
    }

    @Test
    fun `while validating the Apply label is replaced by a spinner`() {
        show(stateOf(validating = true))

        compose.onNodeWithText("Apply").assertDoesNotExist()
    }

    @Test
    fun `each coupon error has its own message`() {
        val messages = mapOf(
            CouponError.NOT_FOUND to "That code doesn't exist.",
            CouponError.BELOW_MINIMUM to "Your cart is below this coupon's minimum.",
            CouponError.NETWORK to "Couldn't reach the server. Try again.",
        )
        var state by mutableStateOf(stateOf(error = CouponError.NOT_FOUND))
        compose.setContent { MaterialTheme { CheckoutScreen(state = state, onIntent = {}) } }

        messages.forEach { (error, message) ->
            state = stateOf(error = error)
            compose.onNodeWithText(message).assertIsDisplayed()
        }
    }

    @Test
    fun `applied coupon is confirmed and its discount shown`() {
        show(stateOf(coupon = AmountOff("FIVEOFF", 0, 500)))

        compose.onNodeWithText("FIVEOFF applied").assertIsDisplayed()
        compose.onNodeWithTag("discountRow").assertExists()
        compose.onNodeWithText("-$5.00").assertIsDisplayed()
    }

    @Test
    fun `an error takes precedence over the applied confirmation`() {
        show(stateOf(coupon = AmountOff("FIVEOFF", 0, 500), error = CouponError.NETWORK))

        compose.onNodeWithText("Couldn't reach the server. Try again.").assertIsDisplayed()
        compose.onNodeWithText("FIVEOFF applied").assertDoesNotExist()
    }

    @Test
    fun `no discount row without a discount`() {
        show(stateOf())

        compose.onNodeWithTag("discountRow").assertDoesNotExist()
    }

    @Test
    fun `points field keeps only digits and Use dispatches the amount`() {
        show(stateOf())

        pointsField().performTextInput("1a2-3")
        compose.onNodeWithText("123").assertExists()
        button("Use").performClick()

        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.UsePoints(123)), intents)
    }

    @Test
    fun `Use with an empty points field dispatches zero`() {
        show(stateOf())

        button("Use").performClick()

        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.UsePoints(0)), intents)
    }

    @Test
    fun `Save cart dispatches SaveCart`() {
        show(stateOf())

        button("Save cart").assertIsEnabled().performClick()

        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.SaveCart), intents)
    }

    @Test
    fun `Save cart is disabled for an empty cart`() {
        show(stateOf(items = emptyList()))

        button("Save cart").assertIsNotEnabled()
    }
}
