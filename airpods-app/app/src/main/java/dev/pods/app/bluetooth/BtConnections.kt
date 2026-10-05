package dev.pods.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.ParcelUuid
import dev.pods.app.data.DeviceInfo
import dev.pods.app.data.PodsRepository
import org.lsposed.hiddenapibypass.HiddenApiBypass

/** Finds AirPods (or Beats) that are connected over Bluetooth audio. */
object BtConnections {
    /** Service UUID Apple headphones expose for their accessory protocol. */
    private val APPLE_AAP_UUID: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

    fun isBluetoothOn(context: Context): Boolean =
        context.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true

    @SuppressLint("MissingPermission")
    fun isApple(device: BluetoothDevice): Boolean {
        val name = displayName(device) ?: ""
        if (listOf("airpods", "beats", "powerbeats").any { name.contains(it, ignoreCase = true) }) return true
        val uuids = try {
            device.uuids
        } catch (e: SecurityException) {
            null
        }
        return uuids?.any { it == APPLE_AAP_UUID } == true
    }

    @SuppressLint("MissingPermission")
    fun displayName(device: BluetoothDevice): String? = try {
        device.alias ?: device.name
    } catch (e: SecurityException) {
        null
    }

    const val ACTION_BATTERY_LEVEL_CHANGED = "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED"
    const val EXTRA_BATTERY_LEVEL = "android.bluetooth.device.extra.BATTERY_LEVEL"

    /** Why the last system battery read failed, for diagnostics. */
    @Volatile
    var systemBatteryStatus: String = "Not read yet"
        private set

    /**
     * Battery the AirPods report to Android over the headset profile (one value,
     * usually the lower bud). The getter is a hidden platform API, so this is
     * best effort.
     */
    fun systemBatteryLevel(device: BluetoothDevice): Int? = try {
        val level = HiddenApiBypass.invoke(BluetoothDevice::class.java, device, "getBatteryLevel") as? Int
        systemBatteryStatus = if (level != null && level in 0..100) "OK" else "Not reported (${level ?: "null"})"
        level?.takeIf { it in 0..100 }
    } catch (e: Throwable) {
        systemBatteryStatus = e.javaClass.simpleName
        null
    }

    /** Refreshes [PodsRepository]'s system battery level for the connected AirPods. */
    @SuppressLint("MissingPermission")
    fun pollSystemBattery(context: Context) {
        val app = context.applicationContext
        val address = PodsRepository.state.value.connected?.address ?: return
        if (!Permissions.hasBluetooth(app)) return
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return
        }
        systemBatteryLevel(device)?.let { PodsRepository.setHeadsetBattery(app, it) }
    }

    fun info(device: BluetoothDevice): DeviceInfo =
        DeviceInfo(displayName(device) ?: "AirPods", device.address)

    /**
     * Asks the A2DP and headset profiles which devices are connected and stores
     * the first AirPods found. [onResult] runs on the main thread.
     */
    @SuppressLint("MissingPermission")
    fun refresh(context: Context, onResult: (DeviceInfo?) -> Unit = {}) {
        val app = context.applicationContext
        if (!Permissions.hasBluetooth(app)) return onResult(null)
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) {
            PodsRepository.setConnected(app, null)
            return onResult(null)
        }
        val profiles = listOf(BluetoothProfile.A2DP, BluetoothProfile.HEADSET)
        var pending = profiles.size
        var found: DeviceInfo? = null

        fun finish() {
            pending -= 1
            if (pending == 0) {
                PodsRepository.setConnected(app, found)
                onResult(found)
            }
        }

        for (profile in profiles) {
            val ok = try {
                adapter.getProfileProxy(app, object : BluetoothProfile.ServiceListener {
                    override fun onServiceConnected(p: Int, proxy: BluetoothProfile) {
                        try {
                            if (found == null) {
                                proxy.connectedDevices.firstOrNull { isApple(it) }?.let { device ->
                                    found = info(device)
                                    systemBatteryLevel(device)?.let { PodsRepository.setHeadsetBattery(app, it) }
                                }
                            }
                        } catch (e: SecurityException) {
                            // Permission revoked mid-flight; treat as not connected.
                        }
                        adapter.closeProfileProxy(p, proxy)
                        finish()
                    }

                    override fun onServiceDisconnected(p: Int) = Unit
                }, profile)
            } catch (e: SecurityException) {
                false
            }
            if (!ok) finish()
        }
    }
}
