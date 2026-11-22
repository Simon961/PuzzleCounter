package com.example.puzzlecounter.analyzer

import android.annotation.SuppressLint
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The guard against a GPU driver that crashes the app. A crash cannot be tested for real, but its trace can: an
 * attempt that was started and never finished, found at the next start. Works on a file of its own.
 */
@RunWith(AndroidJUnit4::class)
class GpuGuardInstrumentedTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, 0)

    /** The guard as it is after a restart of the app: a new object on the same saved state. */
    private fun guard(version: Long = 1) = GpuGuard(prefs, version)

    // commit(): the file is empty before the first test starts, no earlier write may still be on its way
    @SuppressLint("ApplySharedPref")
    @Before
    fun clean() {
        prefs.edit().clear().commit()
    }

    /** Nothing of the test stays on the phone, not even an empty file. */
    @After
    fun removeTheFile() {
        context.deleteSharedPreferences(PREFS_NAME)
    }

    @Test
    fun aWorkingGpu_isAlwaysTried() {
        repeat(5) {
            val guard = guard()
            assertTrue(guard.mayTryGpu())
            guard.attemptStarted()
            guard.attemptSucceeded()
        }
    }

    @Test
    fun oneProcessThatDiedInTheGpu_getsASecondChance() {
        val first = guard()
        assertTrue(first.mayTryGpu())
        first.attemptStarted() // ... and the process is gone
        assertTrue("one unfinished attempt may be a killed process, not a crash", guard().mayTryGpu())
    }

    @Test
    fun twoProcessesInARowThatDiedInTheGpu_leaveTheGpuAlone() {
        repeat(2) {
            val guard = guard()
            assertTrue(guard.mayTryGpu())
            guard.attemptStarted() // process gone
        }
        assertFalse("the third start must not crash again", guard().mayTryGpu())
        assertFalse(guard().mayTryGpu())
    }

    @Test
    fun aSuccessInBetween_clearsTheCount() {
        val first = guard()
        first.mayTryGpu(); first.attemptStarted() // died once
        val second = guard()
        assertTrue(second.mayTryGpu()); second.attemptStarted(); second.attemptSucceeded() // worked
        val third = guard()
        assertTrue(third.mayTryGpu()); third.attemptStarted() // dies again: counts as the first failure again
        assertTrue(guard().mayTryGpu())
    }

    @Test
    fun anErrorTheAppCaught_countsLikeADeath() {
        val first = guard()
        assertTrue(first.mayTryGpu()); first.attemptStarted(); first.attemptFailed(definitive = false)
        val second = guard()
        assertTrue(second.mayTryGpu()); second.attemptStarted(); second.attemptFailed(definitive = false)
        assertFalse(guard().mayTryGpu())
    }

    @Test
    fun wrongResults_closeTheGpuAtOnce() {
        val guard = guard()
        assertTrue(guard.mayTryGpu()); guard.attemptStarted(); guard.attemptFailed(definitive = true)
        assertFalse(guard().mayTryGpu())
    }

    @Test
    fun aNewAppVersion_getsANewChance() {
        val old = guard(version = 1)
        assertTrue(old.mayTryGpu()); old.attemptStarted(); old.attemptFailed(definitive = true)
        assertFalse(guard(version = 1).mayTryGpu())
        assertTrue("a new version may have a fixed driver or delegate", guard(version = 2).mayTryGpu())
    }

    private companion object {
        const val PREFS_NAME = "gpu_guard_test"
    }
}
