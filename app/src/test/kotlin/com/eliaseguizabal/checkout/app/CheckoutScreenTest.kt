package com.eliaseguizabal.checkout.app

import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.Quote
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class CheckoutScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val mug = CartItem(sku = "Mug", unitPriceCents = 3_000, quantity = 1)
    private val pen = CartItem(sku = "Pen", unitPriceCents = 250, quantity = 2)

    private val intents = mutableListOf<CheckoutIntent>()

    private fun show(state: CheckoutState) =
        compose.setContent { CheckoutScreen(state = state, onIntent = { intents += it }) }

    // --- Cart rows -----------------------------------------------------------

    @Test
    fun `cart row shows quantity, unit price and line total`() {
        show(CheckoutState(items = listOf(pen)))

        compose.onNodeWithText("Pen").assertIsDisplayed()
        compose.onNodeWithText("2 × $2.50").assertIsDisplayed()
        compose.onNodeWithText("$5.00").assertIsDisplayed()
    }

    @Test
    fun `remove sends the sku of the row it belongs to`() {
        show(CheckoutState(items = listOf(mug, pen)))

        compose.onAllNodesWithText("Remove")[1].performClick()

        assertEquals(listOf(CheckoutIntent.RemoveItem("Pen")), intents)
    }

    // --- Discount row --------------------------------------------------------

    @Test
    fun `discount row appears when the quote has a discount`() {
        show(CheckoutState(items = listOf(mug), quote = Quote(3_000, 300, 351, 599, 0, 3_650)))

        compose.onNodeWithTag("discountRow").assertIsDisplayed()
        compose.onNodeWithText("-$3.00").assertIsDisplayed()
    }

    @Test
    fun `discount row is hidden when the quote has no discount`() {
        show(CheckoutState(items = listOf(mug), quote = Quote(3_000, 0, 390, 599, 0, 3_989)))

        compose.onNodeWithTag("discountRow").assertDoesNotExist()
    }

    // --- Save ----------------------------------------------------------------

    @Test
    fun `save is disabled for an empty cart`() {
        show(CheckoutState())

        compose.onNodeWithText("Save cart").assertIsNotEnabled().performClick()

        assertEquals(emptyList<CheckoutIntent>(), intents)
    }

    @Test
    fun `save sends SaveCart when the cart has items`() {
        show(CheckoutState(items = listOf(mug)))

        compose.onNodeWithText("Save cart").assertIsEnabled().performClick()

        assertEquals(listOf(CheckoutIntent.SaveCart), intents)
    }

    // --- Coupon field --------------------------------------------------------

    @Test
    fun `apply is disabled until a non-blank code is typed`() {
        show(CheckoutState(items = listOf(mug)))

        compose.onNodeWithText("Apply").assertIsNotEnabled()
        compose.onNodeWithText("Coupon code").performTextInput("   ")
        compose.onNodeWithText("Apply").assertIsNotEnabled().performClick()

        assertEquals(emptyList<CheckoutIntent>(), intents)
    }

    @Test
    fun `while validating the button shows a spinner and ignores taps`() {
        show(CheckoutState(items = listOf(mug), isValidatingCoupon = true))
        compose.onNodeWithText("Coupon code").performTextInput("SAVE10")

        compose.onNodeWithText("Apply").assertDoesNotExist()
        val spinner = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)
        compose.onNode(spinner).assertIsDisplayed()
        compose.onNode(hasClickAction() and hasAnyDescendant(spinner))
            .assertIsNotEnabled()
            .performClick()

        assertEquals(emptyList<CheckoutIntent>(), intents)
    }

    @Test
    fun `no coupon line is shown when nothing is applied`() {
        show(CheckoutState(items = listOf(mug)))

        compose.onNodeWithText("applied", substring = true).assertDoesNotExist()
    }

    // --- Points field --------------------------------------------------------

    @Test
    fun `points field keeps only digits and Use sends them`() {
        show(CheckoutState(items = listOf(mug)))

        compose.onNodeWithText("Loyalty points (cents)").performTextInput("1a2-3")
        compose.onNodeWithText("Loyalty points (cents)").assertTextEquals("Loyalty points (cents)", "123")
        compose.onNodeWithText("Use").performClick()

        assertEquals(listOf(CheckoutIntent.UsePoints(123)), intents)
    }

    @Test
    fun `Use with an empty points field sends zero`() {
        show(CheckoutState(items = listOf(mug)))

        compose.onNodeWithText("Use").performClick()

        assertEquals(listOf(CheckoutIntent.UsePoints(0)), intents)
    }
}
