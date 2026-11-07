package dev.labforge.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class LaunchTest {
    @Test
    fun applicationStarts() {
        val app = ApplicationProvider.getApplicationContext<LabForgeApp>()
        app.onCreate()
        assertNotNull(app)
    }
}
