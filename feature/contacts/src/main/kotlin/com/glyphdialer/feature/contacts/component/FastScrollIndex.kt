// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.contacts.component

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.feature.contacts.list.ContactSectioner

/**
 * The vertical fast-scroll alphabet rail (BUILD_SPEC §8 — "fast-scroll alphabet
 * index"). Renders the full canonical A–Z (+ "#") rail; letters that have no contacts
 * are dimmed and not selectable, matching the platform contacts UX.
 *
 * Tapping or dragging a letter invokes [onLetterSelected] with the active letter so
 * the host can scroll its list to that section. Stateless aside from the touch-derived
 * highlight; the actual list position lives in the screen.
 *
 * @param presentLetters the letters that currently have contacts (drives enablement).
 * @param onLetterSelected called with the letter under the touch as the finger moves.
 * @param modifier layout modifier.
 */
@Composable
fun FastScrollIndex(
    presentLetters: List<Char>,
    onLetterSelected: (Char) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rail = ContactSectioner.ALPHABET
    val present = remember(presentLetters) { presentLetters.toSet() }
    var railHeightPx by remember { mutableStateOf(0) }

    // Resolve the letter under a vertical position and forward it if it has contacts.
    fun selectAt(y: Float) {
        if (railHeightPx <= 0 || rail.isEmpty()) return
        val index = ((y / railHeightPx) * rail.size).toInt().coerceIn(0, rail.size - 1)
        val letter = rail[index]
        if (letter in present) onLetterSelected(letter)
    }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(20.dp)
            .padding(vertical = Dimens.spaceXs)
            .onSizeChanged { railHeightPx = it.height }
            .pointerInput(present, railHeightPx) {
                detectVerticalDragGestures(
                    onDragStart = { offset -> selectAt(offset.y) },
                    onVerticalDrag = { change, _ -> selectAt(change.position.y) },
                )
            }
            .pointerInput(present, railHeightPx) {
                // A simple tap (no drag) should also jump to the letter.
                androidx.compose.foundation.gestures.detectTapGestures { offset -> selectAt(offset.y) }
            }
            .semantics { contentDescription = "Alphabet fast-scroll index" },
        verticalArrangement = Arrangement.spacedBy(0.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        rail.forEach { letter ->
            val enabled = letter in present
            Text(
                text = letter.toString(),
                style = MaterialTheme.typography.labelSmall.merge(NumberStyle),
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                },
            )
        }
    }
}

@Preview(name = "FastScrollIndex", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewFastScrollIndex() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        FastScrollIndex(
            presentLetters = listOf('A', 'C', 'G', 'M', 'T', '#'),
            onLetterSelected = {},
            modifier = Modifier.padding(8.dp),
        )
    }
}
