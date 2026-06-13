// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.glyphdialer.feature.incall.ui.InCallContent
import kotlinx.coroutines.flow.collectLatest

/**
 * Stateful entry point for the full-screen in-call surface (BUILD_SPEC §9/§18).
 *
 * Collects [InCallViewModel.uiState] lifecycle-aware, consumes one-shot
 * [InCallEffect]s (dialpad navigation, dismissal, messages), and delegates rendering
 * to the stateless [InCallContent] (CONVENTIONS.md §5 — state hoisting).
 *
 * @param onNavigateToDialpad host callback for "Add call".
 * @param onCallEnded host callback when no live calls remain (pop/finish).
 */
@Composable
fun InCallScreen(
    onNavigateToDialpad: () -> Unit,
    onCallEnded: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InCallViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(viewModel, lifecycleOwner) {
        viewModel.effects
            .flowWithLifecycle(lifecycleOwner.lifecycle)
            .collectLatest { effect ->
                when (effect) {
                    InCallEffect.NavigateToDialpad -> onNavigateToDialpad()
                    InCallEffect.Dismiss -> onCallEnded()
                    is InCallEffect.ShowMessage ->
                        android.widget.Toast
                            .makeText(context, effect.message, android.widget.Toast.LENGTH_SHORT)
                            .show()
                }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        InCallContent(
            state = uiState,
            onEvent = remember(viewModel) { viewModel::onEvent },
        )
    }
}
