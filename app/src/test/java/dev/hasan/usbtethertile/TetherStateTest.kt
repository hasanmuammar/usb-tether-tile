package dev.hasan.usbtethertile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TetherStateTest {

    @Test
    fun `detects ncm0 as usb tether iface`() {
        assertEquals("ncm0", usbTetherIface(listOf("wlan0", "ncm0")))
    }

    @Test
    fun `detects usb0 as usb tether iface`() {
        assertEquals("usb0", usbTetherIface(listOf("usb0")))
    }

    @Test
    fun `detects rndis0 as usb tether iface`() {
        assertEquals("rndis0", usbTetherIface(listOf("rndis0")))
    }

    @Test
    fun `no usb iface when only wlan`() {
        assertNull(usbTetherIface(listOf("wlan0")))
    }

    @Test
    fun `empty iface list yields null`() {
        assertNull(usbTetherIface(emptyList()))
    }

    @Test
    fun `ready and usb iface is active`() {
        val m = tileModel(ShizukuStatus.READY, "ncm0")
        assertEquals(TileState.ACTIVE, m.state)
        assertEquals("USB tethering", m.subtitle)
    }

    @Test
    fun `ready and no usb iface is inactive`() {
        val m = tileModel(ShizukuStatus.READY, null)
        assertEquals(TileState.INACTIVE, m.state)
        assertEquals("USB tethering", m.subtitle)
    }

    @Test
    fun `shizuku missing shows needs shizuku subtitle`() {
        val m = tileModel(ShizukuStatus.NOT_RUNNING, null)
        assertEquals(TileState.INACTIVE, m.state)
        assertEquals("needs Shizuku", m.subtitle)
    }

    @Test
    fun `needs permission but tethered still active`() {
        val m = tileModel(ShizukuStatus.NEEDS_PERMISSION, "usb0")
        assertEquals(TileState.ACTIVE, m.state)
        assertEquals("needs Shizuku", m.subtitle)
    }
}
