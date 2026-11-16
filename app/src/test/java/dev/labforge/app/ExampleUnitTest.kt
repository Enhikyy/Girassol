package dev.labforge.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun applicationIdentityIsConfigured() {
        assertEquals("app.girassol", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun applicationVersionIsValid() {
        assertTrue("versionCode must be positive", BuildConfig.VERSION_CODE > 0)
        assertTrue("versionName must not be blank", BuildConfig.VERSION_NAME.isNotBlank())
    }
}
