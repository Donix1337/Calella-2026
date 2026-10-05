package dev.pods.app.bluetooth

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

object Permissions {
    val bluetooth = listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)

    fun runtime(): List<String> = buildList {
        addAll(bluetooth)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
    }

    fun hasBluetooth(context: Context): Boolean = bluetooth.all { granted(context, it) }

    fun hasNotifications(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            granted(context, Manifest.permission.POST_NOTIFICATIONS)

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun isBatteryUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

data class PermissionSnapshot(
    val bluetooth: Boolean,
    val notifications: Boolean,
    val overlay: Boolean,
    val batteryUnrestricted: Boolean,
    val bluetoothEnabled: Boolean,
) {
    companion object {
        fun read(context: Context) = PermissionSnapshot(
            bluetooth = Permissions.hasBluetooth(context),
            notifications = Permissions.hasNotifications(context),
            overlay = Permissions.canDrawOverlays(context),
            batteryUnrestricted = Permissions.isBatteryUnrestricted(context),
            bluetoothEnabled = BtConnections.isBluetoothOn(context),
        )
    }
}
