package com.eliaseguizabal.checkout.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.eliaseguizabal.checkout.domain.CartItem
import com.eliaseguizabal.checkout.store.CheckoutIntent
import com.eliaseguizabal.checkout.store.CheckoutState
import com.eliaseguizabal.checkout.store.CouponError

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    state: CheckoutState,
    onIntent: (CheckoutIntent) -> Unit,
) {
    Scaffold(topBar = { TopAppBar(title = { Text("Checkout") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.items, key = { it.sku }) { item ->
                CartRow(item = item, onRemove = { onIntent(CheckoutIntent.RemoveItem(item.sku)) })
            }
            item {
                CouponField(
                    isValidating = state.isValidatingCoupon,
                    appliedCode = state.appliedCoupon?.code,
                    error = state.couponError,
                    onApply = { onIntent(CheckoutIntent.ApplyCoupon(it)) },
                )
            }
            item { PointsField(onUse = { onIntent(CheckoutIntent.UsePoints(it)) }) }
            item {
                QuoteSummary(quote = state.quote, showDiscountRow = state.quote.discountCents > 0)
            }
            item {
                Button(
                    onClick = { onIntent(CheckoutIntent.SaveCart) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = state.items.isNotEmpty(),
                ) { Text("Save cart") }
            }
        }
    }
}

@Composable
private fun CartRow(item: CartItem, onRemove: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(item.sku, style = MaterialTheme.typography.bodyLarge)
            Text(
                "${item.quantity} × ${formatCents(item.unitPriceCents)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatCents(item.unitPriceCents * item.quantity))
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
private fun CouponField(
    isValidating: Boolean,
    appliedCode: String?,
    error: CouponError?,
    onApply: (String) -> Unit,
) {
    var code by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it },
                label = { Text("Coupon code") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = { onApply(code) }, enabled = !isValidating && code.isNotBlank()) {
                if (isValidating) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text("Apply")
            }
        }
        when {
            error != null -> Text(error.message(), color = MaterialTheme.colorScheme.error)
            appliedCode != null -> Text("$appliedCode applied", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PointsField(onUse: (Long) -> Unit) {
    var points by rememberSaveable { mutableStateOf("") }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = points,
            onValueChange = { value -> points = value.filter(Char::isDigit) },
            label = { Text("Loyalty points (cents)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        OutlinedButton(onClick = { onUse(points.toLongOrNull() ?: 0) }) { Text("Use") }
    }
}

private fun CouponError.message(): String = when (this) {
    CouponError.NOT_FOUND -> "That code doesn't exist."
}
