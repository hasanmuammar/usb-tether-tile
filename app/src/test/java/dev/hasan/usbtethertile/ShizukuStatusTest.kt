package dev.hasan.usbtethertile

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuStatusTest {

    @Test
    fun `not installed wins over everything`() {
        assertEquals(ShizukuStatus.NOT_INSTALLED, mapStatus(false, false, false))
        assertEquals(ShizukuStatus.NOT_INSTALLED, mapStatus(false, true, true))
    }

    @Test
    fun `installed but no binder ping means not running`() {
        assertEquals(ShizukuStatus.NOT_RUNNING, mapStatus(true, false, true))
    }

    @Test
    fun `ping and granted means ready`() {
        assertEquals(ShizukuStatus.READY, mapStatus(true, true, true))
    }

    @Test
    fun `ping but not granted means needs permission`() {
        assertEquals(ShizukuStatus.NEEDS_PERMISSION, mapStatus(true, true, false))
    }
}
