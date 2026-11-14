package dev.hasan.usbtethertile

/** Pure interpretation of a toggle result, for the toast shown to the user. */
data class ToggleOutcome(val success: Boolean, val message: String)

/** Sentinels returned by [PrivilegedTether.setUsbTethering] when the binder
 * call never completes (timeout) or throws before it completes (exception). */
const val TOGGLE_TIMEOUT = Int.MIN_VALUE
const val TOGGLE_EXCEPTION = -1

fun interpretToggleResult(result: Int, enabled: Boolean): ToggleOutcome = when {
    result == 0 -> ToggleOutcome(success = true, message = if (enabled) "USB tethering on" else "USB tethering off")
    result == TOGGLE_TIMEOUT -> ToggleOutcome(success = false, message = "Tethering service did not respond")
    result == TOGGLE_EXCEPTION -> ToggleOutcome(success = false, message = "Could not toggle USB tethering")
    else -> ToggleOutcome(success = false, message = "Failed to toggle USB tethering (error $result)")
}
