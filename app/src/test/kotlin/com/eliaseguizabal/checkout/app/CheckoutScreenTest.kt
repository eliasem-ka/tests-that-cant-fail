package com.eliaseguizabal.checkout.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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

@RunWith(RobolectricTestRunner::class)
class CheckoutScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val calculator = PriceCalculator()
    private val headphones = CartItem("Wireless headphones", 3_999, 1)
    private val cable = CartItem("USB-C cable", 899, 2)

    private fun stateWith(
        items: List<CartItem> = listOf(headphones, cable),
        appliedCoupon: AmountOff? = null,
        couponError: CouponError? = null,
        isValidatingCoupon: Boolean = false,
        loyaltyPointsCents: Long = 0,
    ) = CheckoutState(
        items = items,
        appliedCoupon = appliedCoupon,
        couponError = couponError,
        isValidatingCoupon = isValidatingCoupon,
        loyaltyPointsCents = loyaltyPointsCents,
        quote = calculator.quote(items, appliedCoupon, loyaltyPointsCents),
    )

    private fun setScreen(state: CheckoutState, intents: MutableList<CheckoutIntent>) {
        composeRule.setContent {
            CheckoutScreen(state = state, onIntent = { intents += it })
        }
    }

    // --- cart ---------------------------------------------------------------

    @Test
    fun `cart rows show name, quantity, unit price and line total`() {
        setScreen(stateWith(), mutableListOf())

        composeRule.onNodeWithText("Wireless headphones").assertIsDisplayed()
        composeRule.onNodeWithText("USB-C cable").assertIsDisplayed()
        composeRule.onNodeWithText("2 × $8.99").assertIsDisplayed()
        composeRule.onNodeWithText("$17.98").assertIsDisplayed()
    }

    @Test
    fun `Remove sends RemoveItem for that row`() {
        val intents = mutableListOf<CheckoutIntent>()
        setScreen(stateWith(), intents)

        composeRule.onAllNodesWithText("Remove")[0].performClick()

        assertEquals(listOf(CheckoutIntent.RemoveItem("Wireless headphones")), intents)
    }

    // --- save cart ----------------------------------------------------------

    @Test
    fun `Save cart sends SaveCart when the cart has items`() {
        val intents = mutableListOf<CheckoutIntent>()
        setScreen(stateWith(), intents)

        // the button sits below the fold of the LazyColumn; scroll the list until it is composed
        composeRule.onNode(hasScrollToIndexAction())
            .performScrollToNode(hasText("Save cart"))
        composeRule.onNodeWithText("Save cart").assertIsEnabled().performClick()

        assertEquals(listOf(CheckoutIntent.SaveCart), intents)
    }

    @Test
    fun `Save cart is disabled for an empty cart`() {
        setScreen(stateWith(items = emptyList()), mutableListOf())

        composeRule.onNodeWithText("Save cart").assertIsNotEnabled()
    }

    // --- coupons ------------------------------------------------------------

    @Test
    fun `Apply is disabled until a coupon code is typed`() {
        setScreen(stateWith(), mutableListOf())

        composeRule.onNodeWithText("Apply").assertIsNotEnabled()
    }

    @Test
    fun `Apply sends the typed coupon code`() {
        val intents = mutableListOf<CheckoutIntent>()
        setScreen(stateWith(), intents)

        composeRule.onAllNodes(hasSetTextAction())[0].performClick().performTextInput("save10")
        composeRule.onNodeWithText("Apply").assertIsEnabled().performClick()

        assertEquals(listOf(CheckoutIntent.ApplyCoupon("save10")), intents)
    }

    @Test
    fun `Apply is replaced by a spinner while a coupon is validating`() {
        setScreen(stateWith(isValidatingCoupon = true), mutableListOf())

        composeRule.onAllNodesWithText("Apply").assertCountEquals(0)
    }

    @Test
    fun `an unknown code shows its error`() {
        setScreen(stateWith(couponError = CouponError.NOT_FOUND), mutableListOf())

        composeRule.onNodeWithText("That code doesn't exist.").assertIsDisplayed()
    }

    @Test
    fun `a below-minimum coupon shows its error`() {
        setScreen(stateWith(couponError = CouponError.BELOW_MINIMUM), mutableListOf())

        composeRule.onNodeWithText("Your cart is below this coupon's minimum.").assertIsDisplayed()
    }

    @Test
    fun `a network failure shows its error`() {
        setScreen(stateWith(couponError = CouponError.NETWORK), mutableListOf())

        composeRule.onNodeWithText("Couldn't reach the server. Try again.").assertIsDisplayed()
    }

    @Test
    fun `an applied coupon is confirmed and its discount row is shown`() {
        val fiveOff = AmountOff(code = "FIVEOFF", minSubtotalCents = 0, amountCents = 500)
        setScreen(stateWith(appliedCoupon = fiveOff), mutableListOf())

        composeRule.onNodeWithText("FIVEOFF applied").assertIsDisplayed()
        composeRule.onNodeWithTag("discountRow").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("-$5.00").assertIsDisplayed()
    }

    @Test
    fun `no discount row appears without an applied coupon`() {
        setScreen(stateWith(), mutableListOf())

        composeRule.onAllNodesWithTag("discountRow").assertCountEquals(0)
    }

    // --- loyalty points -----------------------------------------------------

    @Test
    fun `points field keeps digits only and Use sends the value in cents`() {
        val intents = mutableListOf<CheckoutIntent>()
        setScreen(stateWith(), intents)

        // the points field is the second text input on screen, after the coupon field
        composeRule.onAllNodes(hasSetTextAction())[1].performClick().performTextInput("1x2")
        composeRule.onNodeWithText("Use").performClick()

        assertEquals(listOf(CheckoutIntent.UsePoints(12)), intents)
    }

    @Test
    fun `Use with no points entered sends zero`() {
        val intents = mutableListOf<CheckoutIntent>()
        setScreen(stateWith(), intents)

        composeRule.onNodeWithText("Use").performClick()

        assertEquals(listOf(CheckoutIntent.UsePoints(0)), intents)
    }
}
