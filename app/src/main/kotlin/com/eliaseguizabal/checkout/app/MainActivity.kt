package com.eliaseguizabal.checkout.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val viewModel: CheckoutViewModel = viewModel(factory = CheckoutViewModel.Factory)
                val state by viewModel.state.collectAsStateWithLifecycle()
                CheckoutScreen(state = state, onIntent = viewModel::onIntent)
            }
        }
    }
}
