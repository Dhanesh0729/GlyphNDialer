// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer.feature.settings.licenses

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.glyphdialer.core.designsystem.theme.Dimens
import com.glyphdialer.core.designsystem.theme.GlyphTheme
import com.glyphdialer.core.designsystem.theme.NumberStyle
import com.glyphdialer.core.domain.model.ThemeMode
import com.glyphdialer.core.ui.component.DottedDivider

/**
 * Open-source licenses sub-screen (BUILD_SPEC §21 "About / legal").
 *
 * A self-contained, static attribution list for the third-party libraries this app is
 * built on. It deliberately does NOT pull in `play-services-oss-licenses` (which would
 * add a Google Play Services dependency + a Gradle plugin to an otherwise
 * Play-Services-free app); the list below is maintained by hand alongside the version
 * catalog. Keep it in sync when dependencies change (see gradle/libs.versions.toml).
 */

/** A single attributed dependency. */
private data class OpenSourceLibrary(
    val name: String,
    val license: String,
    val copyright: String,
)

/**
 * The libraries bundled into a release build, grouped loosely by area. Versions are
 * intentionally omitted here (they live in the version catalog) so this list doesn't
 * drift on every bump — only names + licenses need maintenance.
 */
private val LIBRARIES: List<OpenSourceLibrary> = listOf(
    OpenSourceLibrary("Jetpack Compose & AndroidX", "Apache License 2.0", "© The Android Open Source Project"),
    OpenSourceLibrary("Material Components for Android", "Apache License 2.0", "© Google LLC"),
    OpenSourceLibrary("Kotlin & Kotlin Coroutines", "Apache License 2.0", "© JetBrains s.r.o. and contributors"),
    OpenSourceLibrary("kotlinx.serialization", "Apache License 2.0", "© JetBrains s.r.o. and contributors"),
    OpenSourceLibrary("kotlinx.collections.immutable", "Apache License 2.0", "© JetBrains s.r.o. and contributors"),
    OpenSourceLibrary("Dagger Hilt", "Apache License 2.0", "© Google LLC"),
    OpenSourceLibrary("AndroidX Room", "Apache License 2.0", "© The Android Open Source Project"),
    OpenSourceLibrary("AndroidX DataStore", "Apache License 2.0", "© The Android Open Source Project"),
    OpenSourceLibrary("AndroidX WorkManager", "Apache License 2.0", "© The Android Open Source Project"),
    OpenSourceLibrary("AndroidX Media3 (ExoPlayer)", "Apache License 2.0", "© The Android Open Source Project"),
    OpenSourceLibrary("AndroidX CameraX", "Apache License 2.0", "© The Android Open Source Project"),
    OpenSourceLibrary("Coil", "Apache License 2.0", "© Coil Contributors"),
    OpenSourceLibrary("Accompanist", "Apache License 2.0", "© Google LLC"),
    OpenSourceLibrary("libphonenumber", "Apache License 2.0", "© Google LLC"),
    OpenSourceLibrary("Ktor", "Apache License 2.0", "© JetBrains s.r.o. and contributors"),
    OpenSourceLibrary("stream-webrtc-android", "Apache License 2.0", "© Stream.io Inc. / WebRTC project authors"),
    OpenSourceLibrary("Lottie for Android", "Apache License 2.0", "© Airbnb, Inc."),
    OpenSourceLibrary("Timber", "Apache License 2.0", "© Jake Wharton"),
    OpenSourceLibrary("Android Desugar JDK Libs", "GNU GPL v2 with Classpath Exception", "© Oracle and/or its affiliates"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesRoute(
    onNavigateUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onNavigateUp)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "OPEN-SOURCE LICENSES",
                        style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = Dimens.screenPadding,
                vertical = Dimens.spaceMd,
            ),
            verticalArrangement = Arrangement.spacedBy(Dimens.spaceXs),
        ) {
            item(key = "intro") {
                Text(
                    text = "Glyph Dialer is built on the open-source software listed below. " +
                        "Full license texts ship with each library and are available at their " +
                        "respective project pages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Dimens.spaceSm),
                )
            }

            items(items = LIBRARIES, key = { it.name }) { lib ->
                LibraryRow(lib)
                DottedDivider(modifier = Modifier.padding(vertical = Dimens.spaceXs))
            }
        }
    }
}

@Composable
private fun LibraryRow(
    lib: OpenSourceLibrary,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().padding(vertical = Dimens.spaceXs)) {
        Text(
            text = lib.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = lib.license,
            style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = lib.copyright,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// --- Preview -----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Licenses", showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun PreviewLicenses() {
    GlyphTheme(themeMode = ThemeMode.DARK) {
        LicensesRoute(onNavigateUp = {})
    }
}
