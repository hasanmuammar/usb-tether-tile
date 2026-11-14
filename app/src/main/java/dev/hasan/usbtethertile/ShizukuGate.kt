package dev.hasan.usbtethertile

import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

/**
 * Checks Shizuku availability/permission state. Thin over the Shizuku API;
 * the pure mapping lives in [mapStatus] and is unit-tested.
 */
/** Pure seam for tests: raw booleans -> status. */
fun mapRaw(installed: Boolean, ping: Boolean, granted: Boolean): ShizukuStatus =
    mapStatus(installed, ping, granted)

object ShizukuGate {

    private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"

    fun check(packageManager: PackageManager): ShizukuStatus {
        val installed = try {
            packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
        val ping = installed && Shizuku.pingBinder()
        val granted = ping && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        return mapStatus(installed, ping, granted)
    }
}
