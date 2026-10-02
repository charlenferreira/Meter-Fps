package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.HudConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Meter FPS", appName)
    }

    @Test
    fun `hud config default values and persistence`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = HudConfig.load(context)
        assertNotNull(config)
        assertTrue(config.scalePercent in 70..150)
        assertTrue(config.opacityPercent in 30..100)
        assertTrue(config.showFps)
    }
}
