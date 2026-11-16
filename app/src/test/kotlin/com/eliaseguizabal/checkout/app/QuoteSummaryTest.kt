package com.eliaseguizabal.checkout.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eliaseguizabal.checkout.domain.Quote
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class QuoteSummaryTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(quote: Quote, showDiscountRow: Boolean = false) =
        compose.setContent { QuoteSummary(quote = quote, showDiscountRow = showDiscountRow) }

    @Test
    fun `shows every amount of the quote`() {
        show(Quote(3_000, 300, 351, 599, 0, 3_650), showDiscountRow = true)

        compose.onNodeWithText("$30.00").assertIsDisplayed()
        compose.onNodeWithText("-$3.00").assertIsDisplayed()
        compose.onNodeWithText("$3.51").assertIsDisplayed()
        compose.onNodeWithText("$5.99").assertIsDisplayed()
        compose.onNodeWithText("$36.50").assertIsDisplayed()
    }

    @Test
    fun `discount row is hidden unless asked for`() {
        show(Quote(3_000, 300, 351, 599, 0, 3_650), showDiscountRow = false)

        compose.onNodeWithTag("discountRow").assertDoesNotExist()
        compose.onNodeWithText("-$3.00").assertDoesNotExist()
    }

    @Test
    fun `zero shipping on a non-empty cart reads Free`() {
        show(Quote(6_000, 0, 780, 0, 0, 6_780))

        compose.onNodeWithText("Free").assertIsDisplayed()
        compose.onNodeWithText("$0.00").assertDoesNotExist()
    }

    @Test
    fun `empty cart shows zero shipping, not Free`() {
        show(Quote.EMPTY)

        compose.onNodeWithText("Free").assertDoesNotExist()
        // Subtotal, tax, shipping and total.
        compose.onAllNodesWithText("$0.00").assertCountEquals(4)
    }

    @Test
    fun `points row shows the points taken off`() {
        show(Quote(3_000, 0, 390, 599, 989, 3_000))

        compose.onNodeWithText("Points").assertIsDisplayed()
        compose.onNodeWithText("-$9.89").assertIsDisplayed()
    }

    @Test
    fun `points row is hidden when no points are used`() {
        show(Quote(3_000, 0, 390, 599, 0, 3_989))

        compose.onNodeWithText("Points").assertDoesNotExist()
    }
}
