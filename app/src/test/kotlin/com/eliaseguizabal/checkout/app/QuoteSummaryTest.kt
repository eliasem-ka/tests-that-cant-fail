package com.eliaseguizabal.checkout.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eliaseguizabal.checkout.domain.Quote
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuoteSummaryTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val quoteWithFreeShipping = Quote(
        subtotalCents = 6_000,
        discountCents = 0,
        taxCents = 780,
        shippingCents = 0,
        pointsAppliedCents = 0,
        totalCents = 6_780,
    )

    @Test
    fun `shows every line with formatted amounts`() {
        composeRule.setContent { QuoteSummary(quote = quoteWithFreeShipping) }

        composeRule.onNodeWithText("Subtotal").assertIsDisplayed()
        composeRule.onNodeWithText("$60.00").assertIsDisplayed()
        composeRule.onNodeWithText("Tax").assertIsDisplayed()
        composeRule.onNodeWithText("$7.80").assertIsDisplayed()
        composeRule.onNodeWithText("Total").assertIsDisplayed()
        composeRule.onNodeWithText("$67.80").assertIsDisplayed()
    }

    @Test
    fun `zero shipping on a non-empty order is shown as Free`() {
        composeRule.setContent { QuoteSummary(quote = quoteWithFreeShipping) }

        composeRule.onNodeWithText("Free").assertIsDisplayed()
    }

    @Test
    fun `paid shipping shows its fee`() {
        val quote = quoteWithFreeShipping.copy(shippingCents = 599, totalCents = 7_379)
        composeRule.setContent { QuoteSummary(quote = quote) }

        composeRule.onNodeWithText("$5.99").assertIsDisplayed()
        composeRule.onAllNodesWithText("Free").assertCountEquals(0)
    }

    @Test
    fun `empty order does not claim free shipping`() {
        composeRule.setContent { QuoteSummary(quote = Quote.EMPTY) }

        composeRule.onAllNodesWithText("Free").assertCountEquals(0)
        // subtotal, tax, shipping and total all read $0.00 here
        composeRule.onAllNodesWithText("$0.00")[0].assertIsDisplayed()
    }

    @Test
    fun `discount row is hidden by default`() {
        val quote = quoteWithFreeShipping.copy(discountCents = 500)
        composeRule.setContent { QuoteSummary(quote = quote) }

        composeRule.onAllNodesWithTag("discountRow").assertCountEquals(0)
    }

    @Test
    fun `discount row is shown with a negated amount when requested`() {
        val quote = quoteWithFreeShipping.copy(discountCents = 500)
        composeRule.setContent { QuoteSummary(quote = quote, showDiscountRow = true) }

        composeRule.onNodeWithTag("discountRow").assertIsDisplayed()
        composeRule.onNodeWithText("-$5.00").assertIsDisplayed()
    }

    @Test
    fun `points row is hidden when no points are applied`() {
        composeRule.setContent { QuoteSummary(quote = quoteWithFreeShipping) }

        composeRule.onAllNodesWithText("Points").assertCountEquals(0)
    }

    @Test
    fun `points row shows the applied amount negated`() {
        val quote = quoteWithFreeShipping.copy(pointsAppliedCents = 200, totalCents = 6_580)
        composeRule.setContent { QuoteSummary(quote = quote) }

        composeRule.onNodeWithText("Points").assertIsDisplayed()
        composeRule.onNodeWithText("-$2.00").assertIsDisplayed()
        composeRule.onNodeWithText("$65.80").assertIsDisplayed()
    }
}
