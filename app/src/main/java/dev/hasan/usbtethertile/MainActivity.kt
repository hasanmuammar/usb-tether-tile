package dev.hasan.usbtethertile

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var actionButton: Button
    private lateinit var settingsButton: Button

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            statusText.post {
                refresh()
                Toast.makeText(
                    this,
                    if (grantResult == PackageManager.PERMISSION_GRANTED) "Shizuku granted"
                    else "Shizuku permission denied",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.status_text)
        actionButton = findViewById(R.id.action_button)
        settingsButton = findViewById(R.id.settings_button)

        actionButton.setOnClickListener { requestOrInstall() }
        settingsButton.setOnClickListener { openTetherSettings() }

        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        refresh()
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        super.onDestroy()
    }

    private fun refresh() {
        when (ShizukuGate.check(packageManager)) {
            ShizukuStatus.READY -> {
                statusText.text = getString(R.string.status_ready)
                actionButton.text = getString(R.string.action_toggle_from_tile)
                actionButton.isEnabled = false
            }
            ShizukuStatus.NEEDS_PERMISSION -> {
                statusText.text = getString(R.string.status_needs_permission)
                actionButton.text = getString(R.string.action_grant)
                actionButton.isEnabled = true
            }
            ShizukuStatus.NOT_RUNNING -> {
                statusText.text = getString(R.string.status_not_running)
                actionButton.text = getString(R.string.action_open_shizuku)
                actionButton.isEnabled = true
            }
            ShizukuStatus.NOT_INSTALLED -> {
                statusText.text = getString(R.string.status_not_installed)
                actionButton.text = getString(R.string.action_install_shizuku)
                actionButton.isEnabled = true
            }
        }
    }

    private fun requestOrInstall() {
        when (ShizukuGate.check(packageManager)) {
            ShizukuStatus.NEEDS_PERMISSION -> Shizuku.requestPermission(REQUEST_CODE)
            ShizukuStatus.NOT_RUNNING -> {
                val launch = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE)
                if (launch != null) startActivity(launch)
                else Toast.makeText(this, "Shizuku app not found", Toast.LENGTH_SHORT).show()
            }
            ShizukuStatus.NOT_INSTALLED -> {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_SITE)))
                } catch (_: Exception) {
                    Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show()
                }
            }
            else -> refresh()
        }
    }

    private fun openTetherSettings() {
        val first = tetherSettingsIntents().firstOrNull {
            try {
                startActivity(it)
                true
            } catch (_: Exception) {
                false
            }
        }
        if (first == null) Toast.makeText(this, "No tethering settings found", Toast.LENGTH_SHORT).show()
    }

    companion object {
        private const val REQUEST_CODE = 1
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_SITE = "https://shizuku.rikka.app/"
    }
}
