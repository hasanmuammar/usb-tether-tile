package dev.hasan.usbtethertile

import org.junit.Assert.assertEquals
import org.junit.Test

class ShizukuGateTest {

    /** Pure seam over the raw Shizuku checks; runtime branches pinned here. */
    @Test
    fun `seam maps not installed`() {
        assertEquals(ShizukuStatus.NOT_INSTALLED, mapRaw(installed = false, ping = false, granted = false))
    }

    @Test
    fun `seam maps not running`() {
        assertEquals(ShizukuStatus.NOT_RUNNING, mapRaw(installed = true, ping = false, granted = true))
    }

    @Test
    fun `seam maps ready`() {
        assertEquals(ShizukuStatus.READY, mapRaw(installed = true, ping = true, granted = true))
    }

    @Test
    fun `seam maps needs permission`() {
        assertEquals(ShizukuStatus.NEEDS_PERMISSION, mapRaw(installed = true, ping = true, granted = false))
    }
}
