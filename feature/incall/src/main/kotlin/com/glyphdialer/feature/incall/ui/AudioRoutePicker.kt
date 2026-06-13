// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.incall.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.AudioRoute
import com.glyphdialer.core.domain.model.AudioState
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.EngineeredCard

/**
 * The audio-route picker (BUILD_SPEC §9 — "Speaker/route picker:
 * earpiece/speaker/BT/wired").
 *
 * Lists ONLY the routes the system currently reports as supported
 * ([AudioState.supportedRoutes]) — honest about what the device offers (e.g. no BT
 * row when no headset is connected). The active route is highlighted; tapping a row
 * raises [onSelect].
 */
@Composable
fun AudioRoutePicker(
    audioState: AudioState,
    onSelect: (AudioRoute) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routes = AudioRoute.entries.filter { audioState.supports(it) }
    EngineeredCard(
        modifier = modifier.fillMaxWidth(),
        indexLabel = "ROUTE",
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Dimens.spaceSm)) {
            routes.forEach { route ->
                val selected = route == audioState.route
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = { onSelect(route) },
                        )
                        .padding(vertical = Dimens.spaceXs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMd),
                ) {
                    Icon(
                        imageVector = route.icon(),
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconButton.div(2).plus(4.dp.times(2))),
                    )
                    Text(
                        text = route.label() + (audioState.bluetoothDeviceName
                            ?.takeIf { route == AudioRoute.BLUETOOTH }
                            ?.let { " · ${it.uppercase()}" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium.merge(NumberStyle),
                        color = if (selected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Material icon for an [AudioRoute]. */
internal fun AudioRoute.icon(): ImageVector = when (this) {
    AudioRoute.EARPIECE -> Icons.Filled.PhoneInTalk
    AudioRoute.SPEAKER -> Icons.Filled.VolumeUp
    AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
    AudioRoute.WIRED_HEADSET -> Icons.Filled.Headset
}

/** Short uppercase label for an [AudioRoute]. */
internal fun AudioRoute.label(): String = when (this) {
    AudioRoute.EARPIECE -> "EARPIECE"
    AudioRoute.SPEAKER -> "SPEAKER"
    AudioRoute.BLUETOOTH -> "BLUETOOTH"
    AudioRoute.WIRED_HEADSET -> "WIRED"
}

@Preview(name = "AudioRoutePicker", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewRoutePicker() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        AudioRoutePicker(
            audioState = AudioState(
                route = AudioRoute.SPEAKER,
                supportedRoutes = setOf(
                    AudioRoute.EARPIECE,
                    AudioRoute.SPEAKER,
                    AudioRoute.BLUETOOTH,
                ),
                bluetoothDeviceName = "Ear (2)",
            ),
            onSelect = {},
            onDismiss = {},
        )
    }
}
