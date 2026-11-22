package com.example.puzzlecounter.analyzer

import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Keeps the app from crashing at every start on a phone whose GPU driver cannot run the model.
 *
 * A GPU delegate that goes wrong inside the driver kills the whole process - there is nothing in Kotlin to catch, and
 * as the model is loaded when the app starts, the app would crash again at every start. So the attempt is written
 * down ([attemptStarted]) before the GPU is used and crossed out when it worked ([attemptSucceeded]). An attempt that
 * is still open at the next start means that the process died in it; after [MAX_FAILURES] such starts in a row the
 * GPU is left alone and the model runs on the CPU. One failure is not enough, because the system or the user may also
 * end the process while the model is being loaded. A new app version gets a new chance, as the driver or the
 * delegate may have changed.
 *
 * The state is written synchronously (`commit = true`): the process may be gone a moment later.
 */
internal class GpuGuard(private val prefs: SharedPreferences, private val appVersion: Long) {

    /** Whether the GPU may be tried now. Counts an attempt that never finished as a failure. */
    fun mayTryGpu(): Boolean {
        var failures = prefs.getInt(FAILURES, 0)
        if (prefs.getLong(VERSION, NO_VERSION) != appVersion) {
            failures = 0
        } else if (prefs.getBoolean(PENDING, false)) {
            failures += 1
        }
        prefs.edit(commit = true) {
            putLong(VERSION, appVersion)
            putBoolean(PENDING, false)
            putInt(FAILURES, failures)
        }
        return failures < MAX_FAILURES
    }

    /** The GPU is about to be used. */
    fun attemptStarted() {
        prefs.edit(commit = true) { putBoolean(PENDING, true) }
    }

    /** The GPU loaded the model and gave plausible results. */
    fun attemptSucceeded() {
        prefs.edit(commit = true) {
            putBoolean(PENDING, false)
            putInt(FAILURES, 0)
        }
    }

    /**
     * The GPU failed with an error the app could catch. A [definitive] failure (wrong results, which will be the
     * same next time) closes the GPU for this app version at once.
     */
    fun attemptFailed(definitive: Boolean) {
        val failures = if (definitive) MAX_FAILURES else prefs.getInt(FAILURES, 0) + 1
        prefs.edit(commit = true) {
            putBoolean(PENDING, false)
            putInt(FAILURES, failures)
        }
    }

    private companion object {
        const val VERSION = "app_version"
        const val PENDING = "attempt_open"
        const val FAILURES = "failures"
        const val NO_VERSION = -1L
        const val MAX_FAILURES = 2
    }
}
