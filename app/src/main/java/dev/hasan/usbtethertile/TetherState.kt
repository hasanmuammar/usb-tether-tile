package dev.hasan.usbtethertile

/** Pure USB-tether iface detection and tile-state computation. */
private val USB_PREFIXES = listOf("usb", "ncm", "rndis")

/** First tethered iface name that looks like a USB tethering interface, else null. */
fun usbTetherIface(names: Collection<String>): String? =
    names.firstOrNull { name -> USB_PREFIXES.any { name.startsWith(it) } }

enum class TileState { ACTIVE, INACTIVE }

data class TileModel(val state: TileState, val subtitle: String?)

fun tileModel(status: ShizukuStatus, usbIface: String?): TileModel = TileModel(
    state = if (usbIface != null) TileState.ACTIVE else TileState.INACTIVE,
    subtitle = if (status == ShizukuStatus.READY) "USB tethering" else "needs Shizuku"
)
