package dev.hasan.usbtethertile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SettingsFallbackTest {

    @Test
    fun `first fallback is tether settings action`() {
        assertEquals("android.settings.TETHER_SETTINGS", tetherSettingsSpecs()[0].action)
        assertNull(tetherSettingsSpecs()[0].className)
    }

    @Test
    fun `second fallback is settings TetherSettings component`() {
        assertEquals("com.android.settings", tetherSettingsSpecs()[1].packageName)
        assertEquals("com.android.settings.TetherSettings", tetherSettingsSpecs()[1].className)
    }

    @Test
    fun `third fallback is wireless settings action`() {
        assertEquals("android.settings.WIRELESS_SETTINGS", tetherSettingsSpecs()[2].action)
        assertNull(tetherSettingsSpecs()[2].className)
    }

    @Test
    fun `exactly three fallbacks`() {
        assertEquals(3, tetherSettingsSpecs().size)
    }
}
