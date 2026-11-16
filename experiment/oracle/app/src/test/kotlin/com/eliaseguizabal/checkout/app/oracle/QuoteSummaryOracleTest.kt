package com.eliaseguizabal.checkout.app.oracle

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import com.eliaseguizabal.checkout.app.CheckoutScreen
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.domain.Coupon
import com.eliaseguizabal.checkout.domain.PercentOff
import com.eliaseguizabal.checkout.domain.PriceCalculator
import com.eliaseguizabal.checkout.store.CheckoutState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class QuoteSummaryOracleTest {

    @get:Rule
    val compose = createComposeRule()

    private val save10 = PercentOff("SAVE10", 2_000, 10, 1_500)

    private fun state(coupon: Coupon?): CheckoutState {
        val items = listOf(CartItem("A", 2000, 1))
        return CheckoutState(
            items = items,
            appliedCoupon = coupon,
            quote = PriceCalculator().quote(items, coupon, 0),
        )
    }

    @Test
    fun `discount row is shown when the quote has a discount`() {
        compose.setContent { CheckoutScreen(state = state(save10), onIntent = {}) }

        compose.onNodeWithTag("discountRow").assertIsDisplayed()
        compose.onNodeWithText("-$2.00").assertExists()
    }

    @Test
    fun `discount row is hidden without a discount`() {
        compose.setContent { CheckoutScreen(state = state(null), onIntent = {}) }

        compose.onNodeWithTag("discountRow").assertDoesNotExist()
    }
}
