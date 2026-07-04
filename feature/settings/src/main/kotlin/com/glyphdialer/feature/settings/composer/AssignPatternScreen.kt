// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.composer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.Contact
import com.glyphdialer.core.ui.component.DotMatrixSpinner

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssignPatternRoute(
    onNavigateUp: () -> Unit,
    viewModel: AssignPatternViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "ASSIGN TO CONTACTS",
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.saveAssignments()
                            onNavigateUp()
                        }
                    ) {
                        Text("SAVE")
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                DotMatrixSpinner(size = 40.dp)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(horizontal = Dimens.screenPadding, vertical = Dimens.spaceMd)
            ) {
                items(uiState.contacts) { contact ->
                    val isSelected = uiState.selectedContactKeys.contains(contact.lookupKey)
                    ContactSelectionRow(
                        contact = contact,
                        isSelected = isSelected,
                        onToggle = { viewModel.toggleSelection(contact.lookupKey) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactSelectionRow(
    contact: Contact,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = Dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = null
        )
        Spacer(modifier = Modifier.width(Dimens.spaceMd))
        Column {
            Text(
                text = contact.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            val number = contact.primaryNumber?.formatted ?: ""
            if (number.isNotEmpty()) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
