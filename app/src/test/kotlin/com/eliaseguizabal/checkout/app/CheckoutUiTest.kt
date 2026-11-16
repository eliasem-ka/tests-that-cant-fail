package com.eliaseguizabal.checkout.app

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import com.eliaseguizabal.checkout.domain.AmountOff
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.Quote
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CouponError
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CheckoutUiTest {
    @get:Rule val rule = createComposeRule()

    private val quote = Quote(10_000, 500, 1_235, 0, 200, 10_535)

    @Test fun quoteSummaryShowsAllRows() {
        rule.setContent { QuoteSummary(quote, showDiscountRow = true) }
        rule.onNodeWithText("$100.00").assertIsDisplayed()
        rule.onNodeWithText("-$5.00").assertIsDisplayed()
        rule.onNodeWithText("$12.35").assertIsDisplayed()
        rule.onNodeWithText("Free").assertIsDisplayed()
        rule.onNodeWithText("-$2.00").assertIsDisplayed()
        rule.onNodeWithText("$105.35").assertIsDisplayed()
        rule.onNodeWithTag("discountRow").assertIsDisplayed()
    }

    @Test fun quoteSummaryHidesOptionalRows() {
        rule.setContent { QuoteSummary(Quote(1_000, 0, 130, 599, 0, 1_729)) }
        rule.onNodeWithTag("discountRow").assertDoesNotExist()
        rule.onNodeWithText("Points").assertDoesNotExist()
        rule.onNodeWithText("$5.99").assertIsDisplayed()
    }

    @Test fun emptyQuoteShowsPaidShippingNotFree() {
        rule.setContent { QuoteSummary(Quote.EMPTY) }
        rule.onNodeWithText("Free").assertDoesNotExist()
    }

    @Test fun screenListsItemsAndRemoveDispatches() {
        val intents = mutableListOf<CheckoutIntent>()
        val state = CheckoutState(items = listOf(CartItem("USB-C cable", 899, 2)))
        rule.setContent { CheckoutScreen(state, intents::add) }
        rule.onNodeWithText("USB-C cable").assertIsDisplayed()
        rule.onNodeWithText("2 × $8.99").assertIsDisplayed()
        rule.onNodeWithText("$17.98").assertIsDisplayed()
        rule.onNodeWithText("Remove").performClick()
        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.RemoveItem("USB-C cable")), intents)
    }

    @Test fun saveCartDisabledWhenEmptyEnabledOtherwise() {
        rule.setContent { CheckoutScreen(CheckoutState(), {}) }
        rule.onNodeWithText("Save cart").assertIsNotEnabled()
    }

    @Test fun saveCartDispatches() {
        val intents = mutableListOf<CheckoutIntent>()
        rule.setContent { CheckoutScreen(CheckoutState(items = listOf(CartItem("a", 100, 1))), intents::add) }
        rule.onNode(hasScrollAction()).performScrollToNode(hasText("Save cart"))
        rule.onNodeWithText("Save cart").assertIsEnabled().performClick()
        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.SaveCart), intents)
    }

    @Test fun applyCouponDisabledUntilTextEntered() {
        val intents = mutableListOf<CheckoutIntent>()
        rule.setContent { CheckoutScreen(CheckoutState(), intents::add) }
        rule.onNodeWithText("Apply").assertIsNotEnabled()
        rule.onNodeWithText("Coupon code").performTextInput("SAVE10")
        rule.onNodeWithText("Apply").assertIsEnabled().performClick()
        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.ApplyCoupon("SAVE10")), intents)
    }

    @Test fun usePointsDispatchesDigitsOnly() {
        val intents = mutableListOf<CheckoutIntent>()
        rule.setContent { CheckoutScreen(CheckoutState(), intents::add) }
        rule.onNodeWithText("Loyalty points (cents)").performTextInput("1a2")
        rule.onNodeWithText("Use").performClick()
        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.UsePoints(12)), intents)
    }

    @Test fun usePointsWithEmptyFieldDispatchesZero() {
        val intents = mutableListOf<CheckoutIntent>()
        rule.setContent { CheckoutScreen(CheckoutState(), intents::add) }
        rule.onNodeWithText("Use").performClick()
        assertEquals(listOf<CheckoutIntent>(CheckoutIntent.UsePoints(0)), intents)
    }

    private fun assertError(err: CouponError, msg: String) {
        rule.setContent { CheckoutScreen(CheckoutState(couponError = err), {}) }
        rule.onNodeWithText(msg).assertIsDisplayed()
    }

    @Test fun notFoundErrorShown() = assertError(CouponError.NOT_FOUND, "That code doesn't exist.")
    @Test fun belowMinimumErrorShown() = assertError(CouponError.BELOW_MINIMUM, "Your cart is below this coupon's minimum.")
    @Test fun networkErrorShown() = assertError(CouponError.NETWORK, "Couldn't reach the server. Try again.")

    @Test fun appliedCouponIsShown() {
        rule.setContent { CheckoutScreen(CheckoutState(appliedCoupon = AmountOff("FIVEOFF", 0, 500)), {}) }
        rule.onNodeWithText("FIVEOFF applied").assertIsDisplayed()
    }

    @Test fun screenShowsDiscountRowOnlyWhenThereIsADiscount() {
        val withDiscount = CheckoutState(items = listOf(CartItem("a", 1_000, 1)), quote = Quote(1_000, 500, 65, 599, 0, 1_164))
        rule.setContent { CheckoutScreen(withDiscount, {}) }
        rule.onNodeWithTag("discountRow").assertIsDisplayed()
        rule.onNodeWithText("-$5.00").assertIsDisplayed()
    }

    @Test fun screenHidesDiscountRowWithoutDiscount() {
        val noDiscount = CheckoutState(items = listOf(CartItem("a", 1_000, 1)), quote = Quote(1_000, 0, 130, 599, 0, 1_729))
        rule.setContent { CheckoutScreen(noDiscount, {}) }
        rule.onNodeWithTag("discountRow").assertDoesNotExist()
    }

    @Test fun applyIsDisabledAndShowsSpinnerWhileValidating() {
        val intents = mutableListOf<CheckoutIntent>()
        val state = mutableStateOf(CheckoutState())
        rule.setContent { CheckoutScreen(state.value, intents::add) }
        rule.onNodeWithText("Coupon code").performTextInput("SAVE10")
        rule.onNodeWithText("Apply").assertIsEnabled()
        state.value = CheckoutState(isValidatingCoupon = true)
        rule.waitForIdle()
        rule.onNodeWithText("Apply").assertDoesNotExist()
        val applyButton = hasClickAction() and hasAnyDescendant(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate))
        rule.onNode(applyButton).assertIsNotEnabled().performClick()
        assertEquals(emptyList<CheckoutIntent>(), intents)
    }

    @Test fun noSpinnerWhenNotValidating() {
        rule.setContent { CheckoutScreen(CheckoutState(), {}) }
        rule.onNode(hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)).assertDoesNotExist()
    }

    @Test fun errorTakesPrecedenceOverAppliedCouponMessage() {
        val state = CheckoutState(appliedCoupon = AmountOff("FIVEOFF", 0, 500), couponError = CouponError.NETWORK)
        rule.setContent { CheckoutScreen(state, {}) }
        rule.onNodeWithText("Couldn't reach the server. Try again.").assertIsDisplayed()
        rule.onNodeWithText("FIVEOFF applied").assertDoesNotExist()
    }

    @Test fun noCouponMessageByDefault() {
        rule.setContent { CheckoutScreen(CheckoutState(), {}) }
        rule.onNodeWithText("FIVEOFF applied").assertDoesNotExist()
        rule.onNodeWithText("That code doesn't exist.").assertDoesNotExist()
    }
}
