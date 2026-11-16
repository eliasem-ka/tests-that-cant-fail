package com.eliaseguizabal.checkout.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eliaseguizabal.checkout.domain.Quote

@Composable
fun QuoteSummary(
    quote: Quote,
    showDiscountRow: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SummaryRow("Subtotal", formatCents(quote.subtotalCents))
            if (showDiscountRow) {
                SummaryRow(
                    label = "Discount",
                    value = "-" + formatCents(quote.discountCents),
                    modifier = Modifier.testTag("discountRow"),
                )
            }
            SummaryRow("Tax", formatCents(quote.taxCents))
            SummaryRow(
                "Shipping",
                if (quote.shippingCents == 0L && quote.subtotalCents > 0) "Free" else formatCents(quote.shippingCents),
            )
            if (quote.pointsAppliedCents > 0) {
                SummaryRow("Points", "-" + formatCents(quote.pointsAppliedCents))
            }
            HorizontalDivider()
            SummaryRow("Total", formatCents(quote.totalCents), emphasized = true)
        }
    }
}

@Composable
private fun SummaryRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge
    val weight = if (emphasized) FontWeight.Bold else FontWeight.Normal
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = style, fontWeight = weight)
        Text(value, style = style, fontWeight = weight)
    }
}
