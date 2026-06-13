// SPDX-License-Identifier: Apache-2.0
package com.glyphdialer

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import com.glyphdialer.core.domain.glyph.GlyphController
import com.glyphdialer.work.PurgeOldDataWorker
import dagger.hilt.android.HiltAndroidApp
import timber.log.Timber
import javax.inject.Inject

/**
 * Application entry point (CONVENTIONS.md §3 — `:app` wires the DI graph).
 *
 * Responsibilities:
 *  - bootstrap Hilt (`@HiltAndroidApp`),
 *  - initialize Timber for logging (no `Log`/`println` anywhere — §5/§12),
 *  - provide the [HiltWorkerFactory] so `@HiltWorker`s ([PurgeOldDataWorker]) get
 *    their dependencies injected (implements [Configuration.Provider]),
 *  - schedule the periodic retention-purge worker (BUILD_SPEC §12/§19),
 *  - own the best-effort [GlyphController] lifecycle for the process — the controller
 *    no-ops on non-Nothing hardware (§9 HONESTY PRINCIPLE), so this is always safe.
 */
@HiltAndroidApp
class GlyphDialerApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var workManager: WorkManager

    /**
     * Injected so the Glyph session is established (and later released) once for the
     * whole process. On non-Nothing devices this is the no-op controller and every
     * call is harmless (§9).
     */
    @Inject lateinit var glyphController: GlyphController

    /**
     * WorkManager picks up this configuration via on-demand initialization (the default
     * [androidx.work.WorkManagerInitializer] is removed in the merged manifest by the
     * hilt-work artifact), wiring our [HiltWorkerFactory] so workers can be injected.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // NOTE: in release a crash-reporting tree would be planted here.

        Timber.i(
            "Glyph Dialer starting (glyphAvailable=%b)",
            runCatching { glyphController.isAvailable }.getOrDefault(false),
        )

        // Schedule the daily retention purge. The worker re-reads the retention window
        // from preferences on each run, so a settings change needs no rescheduling.
        runCatching { PurgeOldDataWorker.schedule(workManager) }
            .onFailure { Timber.w(it, "Failed to schedule purge work") }
    }

    /**
     * Best-effort Glyph teardown. [onTerminate] is only reliably called on emulators,
     * but releasing here is harmless and correct where it does run; the
     * [com.glyphdialer.telecom.service.GlyphInCallService] also clears Glyph state on
     * call end, so the strip is never left lit.
     */
    override fun onTerminate() {
        runCatching { glyphController.release() }
            .onFailure { Timber.w(it, "GlyphController release failed") }
        super.onTerminate()
    }
}
