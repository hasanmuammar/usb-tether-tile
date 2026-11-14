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

    private fun openFallback() {
        for (intent in tetherSettingsIntents()) {
            try {
                startActivityAndCollapse(intent)
                return
            } catch (_: Exception) {
                // Try the next fallback.
            }
        }
        Toast.makeText(this, "No tethering settings screen found", Toast.LENGTH_SHORT).show()
    }

    private fun openFallbackViaPendingIntent() {
        // PendingIntent variant is required on API 34+ for tiles.
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
            -1
        }
        android.util.Log.d(TAG, "toggle result=$result")
        main.post {
            if (result == 0) {
                Toast.makeText(
                    this,
                    if (enable) "USB tethering on" else "USB tethering off",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this,
                    "Connect a USB cable (error $result)",
                    Toast.LENGTH_SHORT
                ).show()
                openFallbackViaPendingIntent()
            }
            update()
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
        android.util.Log.d("UsbTetherTile", "onClick fired")
        executor.execute {
            when (status()) {
                ShizukuStatus.READY -> toggleAndReport()
                ShizukuStatus.NEEDS_PERMISSION -> {
                    val intent = Intent(this, MainActivity::class.java)
                    val pi = PendingIntent.getActivity(
                        this, 0, intent, PendingIntent.FLAG_IMMUTABLE
                    )
                    startActivityAndCollapse(pi)
                }
                else -> openFallbackViaPendingIntent()
            }
        }
    }
}
