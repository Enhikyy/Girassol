package dev.labforge.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [26])
class Api26ResourcesTest {

    @Test
    fun themeInflatesOnApi26() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val theme = ctx.resources.newTheme()
        theme.applyStyle(R.style.Theme_LabForge, true)
        val attrs = theme.obtainStyledAttributes(intArrayOf(android.R.attr.windowBackground))
        attrs.recycle()
    }

    @Test
    fun iconsDecode() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        assertNotNull(BitmapFactory.decodeResource(ctx.resources, R.mipmap.ic_launcher))
        assertNotNull(BitmapFactory.decodeResource(ctx.resources, R.mipmap.ic_launcher_round))
        assertNotNull(BitmapFactory.decodeResource(ctx.resources, R.drawable.girassol_mark))
    }
}
