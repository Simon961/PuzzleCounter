package com.example.puzzlecounter

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppConfigTest {

    /** With Auto Backup, a reinstall brings back old scan photos and history ("analysed photos by default"). */
    @Test
    fun backupIsOff_soAReinstallStartsEmpty() {
        val flags = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.flags
        assertEquals(
            "allowBackup muss aus bleiben, sonst spielt Android alte Scans und den Verlauf zurück",
            0,
            flags and ApplicationInfo.FLAG_ALLOW_BACKUP,
        )
    }
}
