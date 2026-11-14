package dev.hasan.usbtethertile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToggleOutcomeTest {

    @Test
    fun `result zero with enable is success on`() {
        val o = interpretToggleResult(0, enabled = true)
        assertTrue(o.success)
        assertEquals("USB tethering on", o.message)
    }

    @Test
    fun `result zero with disable is success off`() {
        val o = interpretToggleResult(0, enabled = false)
        assertTrue(o.success)
        assertEquals("USB tethering off", o.message)
    }

    @Test
    fun `timeout sentinel is a distinct failure`() {
        val o = interpretToggleResult(Int.MIN_VALUE, enabled = true)
        assertFalse(o.success)
        assertEquals("Tethering service did not respond", o.message)
    }

    @Test
    fun `exception sentinel is a distinct failure`() {
        val o = interpretToggleResult(-1, enabled = true)
        assertFalse(o.success)
        assertEquals("Could not toggle USB tethering", o.message)
    }

    @Test
    fun `nonzero result code reports the code`() {
        val o = interpretToggleResult(3, enabled = false)
        assertFalse(o.success)
        assertEquals("Failed to toggle USB tethering (error 3)", o.message)
    }
}
