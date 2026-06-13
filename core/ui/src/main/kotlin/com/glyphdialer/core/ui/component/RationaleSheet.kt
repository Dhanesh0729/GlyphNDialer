// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Button
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphShapes
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.domain.model.ThemeMode

/**
 * A permission-rationale bottom sheet (CONVENTIONS.md §6 — "RationaleSheet";
 * BUILD_SPEC §20 permissions UX).
 *
 * Explains *why* a permission is needed before the system dialog, in the engineered
 * dot-matrix style. Stateless: visibility and the [sheetState] are hoisted by the
 * caller (typically a feature screen driving Accompanist's permission state).
 *
 * Honesty principle (CONVENTIONS.md §9): use [availabilityNote] to state platform
 * limits truthfully *in the rationale itself* — e.g. for RECORD_AUDIO, that
 * third-party apps cannot capture the remote party on stock Android 10+ and only the
 * local side / VoIP is recorded. This keeps the permission ask honest about what the
 * user will actually get.
 *
 * @param title short permission title (e.g. "Microphone access").
 * @param rationale why the app needs it, in plain language.
 * @param onConfirm invoked when the user agrees to proceed to the system prompt.
 * @param onDismiss invoked when the sheet is dismissed.
 * @param modifier layout modifier for the sheet content.
 * @param icon leading icon; defaults to a lock.
 * @param availabilityNote optional honest note about platform limits (§9), or null.
 * @param confirmLabel CTA label.
 * @param dismissLabel secondary label.
 * @param sheetState the hoisted [SheetState].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RationaleSheet(
    title: String,
    rationale: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Lock,
    availabilityNote: String? = null,
    confirmLabel: String = "Continue",
    dismissLabel: String = "Not now",
    sheetState: SheetState = rememberModalBottomSheetState(),
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = GlyphShapes.Sheet,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.screenPadding)
                .padding(bottom = Dimens.spaceXl),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface,
            )

            DotMatrixText(
                text = title.uppercase(),
                dotColor = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = rationale,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )

            if (!availabilityNote.isNullOrBlank()) {
                DottedDivider(modifier = Modifier.padding(vertical = Dimens.spaceXs))
                Text(
                    text = availabilityNote,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = Dimens.spaceSm),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(dismissLabel)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "RationaleSheet (content)", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewRationaleSheetContent() {
    // ModalBottomSheet can't be previewed directly; preview the body layout instead.
    GlyphTheme(themeMode = ThemeMode.DARK) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Dimens.screenPadding),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceLg),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            DotMatrixText(text = "MICROPHONE", dotColor = MaterialTheme.colorScheme.onSurface)
            Text(
                "Used to record calls and power live captions.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
            DottedDivider()
            Text(
                "On Android 10+ only your side / VoIP audio can be recorded.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
