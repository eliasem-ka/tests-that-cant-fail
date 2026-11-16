package com.eliaseguizabal.checkout.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eliaseguizabal.checkout.domain.Quote
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuoteSummaryTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(quote: Quote, showDiscountRow: Boolean = false) =
        compose.setContent { MaterialTheme { QuoteSummary(quote = quote, showDiscountRow = showDiscountRow) } }

    private val paidShipping = Quote(
        subtotalCents = 2_500,
        discountCents = 250,
        taxCents = 293,
        shippingCents = 599,
        pointsAppliedCents = 0,
        totalCents = 3_142,
    )

    @Test
    fun `shows every line formatted as money`() {
        show(paidShipping, showDiscountRow = true)

        compose.onNodeWithText("$25.00").assertIsDisplayed()
        compose.onNodeWithText("-$2.50").assertIsDisplayed()
        compose.onNodeWithText("$2.93").assertIsDisplayed()
        compose.onNodeWithText("$5.99").assertIsDisplayed()
        compose.onNodeWithText("$31.42").assertIsDisplayed()
    }

    @Test
    fun `discount row is hidden unless asked for`() {
        show(paidShipping, showDiscountRow = false)

        compose.onNodeWithTag("discountRow").assertDoesNotExist()
        compose.onNodeWithText("Discount").assertDoesNotExist()
    }

    @Test
    fun `discount row is shown when asked for`() {
        show(paidShipping, showDiscountRow = true)

        compose.onNodeWithTag("discountRow").assertExists()
        compose.onNode(hasText("Discount")).assertIsDisplayed()
    }

    @Test
    fun `zero shipping on a non-empty cart reads Free`() {
        show(paidShipping.copy(subtotalCents = 6_000, shippingCents = 0, totalCents = 6_780))

        compose.onNodeWithText("Free").assertIsDisplayed()
    }

    @Test
    fun `empty cart shows zero shipping, not Free`() {
        show(Quote.EMPTY)

        compose.onNodeWithText("Free").assertDoesNotExist()
        // subtotal, tax, shipping and total all read $0.00
        assertEquals(4, compose.onAllNodes(hasText("$0.00")).fetchSemanticsNodes().size)
    }

    @Test
    fun `points row appears only when points were applied`() {
        show(paidShipping.copy(pointsAppliedCents = 142, totalCents = 3_000))

        compose.onNodeWithText("Points").assertIsDisplayed()
        compose.onNodeWithText("-$1.42").assertIsDisplayed()
    }

    @Test
    fun `no points row without points`() {
        show(paidShipping)

        compose.onNodeWithText("Points").assertDoesNotExist()
    }
}
