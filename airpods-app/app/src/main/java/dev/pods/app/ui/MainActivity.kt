package dev.pods.app.ui

import android.appwidget.AppWidgetManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.pods.app.bluetooth.BtConnections
import dev.pods.app.bluetooth.PermissionSnapshot
import dev.pods.app.bluetooth.Permissions
import dev.pods.app.bluetooth.PodsScanner
import dev.pods.app.bluetooth.ScanPower
import dev.pods.app.data.PodsRepository
import dev.pods.app.data.PodsSettings
import dev.pods.app.data.SettingsValues
import dev.pods.app.service.PodsService
import dev.pods.app.ui.theme.PodsTheme
import dev.pods.app.widget.BatteryWidgetReceiver
import dev.pods.app.widget.WidgetUpdater

class MainActivity : ComponentActivity(), HomeActions {

    private var permissions by mutableStateOf<PermissionSnapshot?>(null)
    private var deniedBefore by mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshPermissions()
        if (Permissions.hasBluetooth(this)) {
            PodsSettings.update { it.copy(onboarded = true) }
            onReady()
            requestBatteryUnrestricted()
        } else {
            deniedBefore = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        refreshPermissions()
        setContent {
            PodsTheme {
                val state by PodsRepository.state.collectAsStateWithLifecycle()
                val signal by PodsRepository.signal.collectAsStateWithLifecycle()
                val settings by PodsSettings.values.collectAsStateWithLifecycle()
                val diagnostics by PodsScanner.diagnostics.collectAsStateWithLifecycle()
                val perms = permissions ?: PermissionSnapshot.read(this)
                Crossfade(targetState = perms.bluetooth, label = "root") { ready ->
                    if (ready) {
                        HomeScreen(state, signal, settings, perms, this@MainActivity, diagnostics = diagnostics)
                    } else {
                        OnboardingScreen(deniedBefore = deniedBefore, onContinue = this@MainActivity::onContinue)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPermissions()
        if (Permissions.hasBluetooth(this)) onReady()
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
        if (PodsRepository.refreshPhoneBattery(this)) WidgetUpdater.request(this)
    }

    override fun onStop() {
        PodsScanner.release(SCAN_CLIENT)
        super.onStop()
    }

    private fun onReady() {
        PodsScanner.request(this, SCAN_CLIENT, ScanPower.HIGH)
        BtConnections.refresh(this) { device ->
            if (device != null && PodsSettings.current.backgroundUpdates) PodsService.start(this)
        }
    }

    private fun refreshPermissions() {
        permissions = PermissionSnapshot.read(this)
    }

    private fun onContinue() {
        if (deniedBefore) {
            openAppSettings()
        } else {
            permissionLauncher.launch(Permissions.runtime().toTypedArray())
        }
    }

    override fun updateSettings(transform: (SettingsValues) -> SettingsValues) {
        val before = PodsSettings.current
        PodsSettings.update(transform)
        val after = PodsSettings.current
        if (before.backgroundUpdates != after.backgroundUpdates) {
            if (after.backgroundUpdates) {
                if (PodsRepository.state.value.isConnected) PodsService.start(this)
            } else {
                PodsService.stop(this)
            }
        }
    }

    override fun requestOverlay() {
        launch(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    override fun requestBatteryUnrestricted() {
        if (Permissions.isBatteryUnrestricted(this)) return
        val asked = launch(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
        )
        if (!asked) openAppSettings()
    }

    override fun openLocationSettings() {
        launch(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    override fun openBluetoothSettings() {
        launch(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    }

    override fun addWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        val pinned = manager?.isRequestPinAppWidgetSupported == true &&
            manager.requestPinAppWidget(ComponentName(this, BatteryWidgetReceiver::class.java), null, null)
        if (!pinned) {
            Toast.makeText(this, "Long-press your Home screen, tap Widgets and find Pods.", Toast.LENGTH_LONG).show()
        }
    }

    private fun openAppSettings() {
        launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
    }

    private fun launch(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val SCAN_CLIENT = "ui"
    }
}
