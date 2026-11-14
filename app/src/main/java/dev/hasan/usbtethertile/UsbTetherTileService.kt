package dev.hasan.usbtethertile

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import java.util.concurrent.Executors

class UsbTetherTileService : TileService() {

    private val TAG = "UsbTetherTile"
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var registration: TetherObservation.Registration? = null

    private fun usbIface(): String? = TetherObservation.usbIface(this)

    private fun status(): ShizukuStatus = ShizukuGate.check(packageManager)

    private fun update() {
        val model = tileModel(status(), usbIface())
        qsTile?.apply {
            state = if (model.state == TileState.ACTIVE) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            subtitle = model.subtitle
            updateTile()
        }
    }

    /** Opens the tethering settings via the fallback chain; UI thread only. */
    private fun openFallback() {
        for (raw in tetherSettingsIntents()) {
            try {
                val pi = PendingIntent.getActivity(
                    this, 0, raw, PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pi)
                return
            } catch (_: Exception) {
                // Try the next fallback.
            }
        }
        Toast.makeText(this, "No tethering settings screen found", Toast.LENGTH_SHORT).show()
    }

    private fun toggleAndReport() {
        val enable = usbIface() == null
        android.util.Log.d(TAG, "toggleAndReport enable=$enable")
        val result = try {
            PrivilegedTether.setUsbTethering(enable)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "toggle failed", e)
            TOGGLE_EXCEPTION
        }
        val outcome = interpretToggleResult(result, enabled = enable)
        android.util.Log.d(TAG, "toggle result=$result")
        main.post {
            Toast.makeText(this, outcome.message, Toast.LENGTH_SHORT).show()
            update()
            if (outcome.success && enable) {
                // Enable "succeeded" per the service; verify a tethered iface
                // actually appears (the service can return 0 and do nothing).
                main.postDelayed({
                    if (usbIface() == null) {
                        Toast.makeText(
                            this, "No USB network appeared — check the cable",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }, CABLE_CHECK_DELAY_MS)
            }
        }
    }

    override fun onStartListening() {
        update()
        registration = TetherObservation.register(this, mainExecutor) {
            update()
        }
    }

    override fun onStopListening() {
        TetherObservation.unregister(this, registration)
        registration = null
    }

    override fun onClick() {
        super.onClick()
        android.util.Log.d(TAG, "onClick fired")
        executor.execute {
            when (status()) {
                ShizukuStatus.READY -> toggleAndReport()
                ShizukuStatus.NEEDS_PERMISSION -> main.post {
                    val intent = Intent(this, MainActivity::class.java)
                    val pi = PendingIntent.getActivity(
                        this, 0, intent, PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pi)
                }
                else -> main.post { openFallback() }
            }
        }
    }

    private companion object {
        const val CABLE_CHECK_DELAY_MS = 2000L
    }
}
