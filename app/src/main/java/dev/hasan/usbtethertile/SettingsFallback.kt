package dev.hasan.usbtethertile

import android.content.Intent

/** Pure description of one tethering-settings fallback target. */
data class TetherFallback(val action: String?, val packageName: String?, val className: String?)

/**
 * Fallback targets to open the system tethering settings, in order of
 * preference (see design doc Global Constraints). Pure: no Context needed;
 * [tetherSettingsIntents] turns them into real Intents.
 */
fun tetherSettingsSpecs(): List<TetherFallback> = listOf(
    TetherFallback(action = "android.settings.TETHER_SETTINGS", packageName = null, className = null),
    TetherFallback(
        action = null,
        packageName = "com.android.settings",
        className = "com.android.settings.TetherSettings"
    ),
    TetherFallback(action = "android.settings.WIRELESS_SETTINGS", packageName = null, className = null)
)

/** Maps the pure fallback specs to launchable Intents (verified on device). */
fun tetherSettingsIntents(): List<Intent> = tetherSettingsSpecs().map { spec ->
    val intent = when {
        spec.action != null -> Intent(spec.action)
        else -> Intent().setClassName(spec.packageName!!, spec.className!!)
    }
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
    intent
}
