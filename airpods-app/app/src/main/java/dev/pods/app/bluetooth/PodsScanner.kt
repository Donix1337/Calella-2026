package dev.pods.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.util.Log
import dev.pods.app.ble.ProximityParser
import dev.pods.app.data.PodsRepository

enum class ScanPower { BALANCED, HIGH }

/**
 * One shared BLE scan for the whole app. The UI and the background service each
 * register a request; the scan runs at the strongest requested power. Android
 * throttles apps that restart scans too often, so we only restart when the
 * effective power actually changes.
 */
object PodsScanner {
    private const val TAG = "PodsScanner"

    private val requests = HashMap<String, ScanPower>()
    private var appContext: Context? = null
    private var callback: ScanCallback? = null
    private var activePower: ScanPower? = null

    @Synchronized
    fun request(context: Context, client: String, power: ScanPower) {
        appContext = context.applicationContext
        requests[client] = power
        apply(force = false)
    }

    @Synchronized
    fun release(client: String) {
        requests.remove(client)
        apply(force = false)
    }

    /** Restart after Bluetooth comes back on or permissions change. */
    @Synchronized
    fun restart() = apply(force = true)

    private fun apply(force: Boolean) {
        val desired = requests.values.maxByOrNull { it.ordinal }
        if (!force && desired == activePower && (desired == null || callback != null)) return
        stopScan()
        if (desired != null) startScan(desired)
    }

    @SuppressLint("MissingPermission")
    private fun startScan(power: ScanPower) {
        val context = appContext ?: return
        if (!Permissions.hasBluetooth(context)) return
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return
        if (!adapter.isEnabled) return
        val scanner = adapter.bluetoothLeScanner ?: return

        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) = handle(context, result)

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handle(context, it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.w(TAG, "Scan failed: $errorCode")
                synchronized(PodsScanner) {
                    if (callback === this) {
                        callback = null
                        activePower = null
                    }
                }
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(
                if (power == ScanPower.HIGH) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED
            )
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setReportDelay(0)
            .build()

        try {
            scanner.startScan(listOf(filter()), settings, cb)
            callback = cb
            activePower = power
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to scan", e)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Bluetooth not ready", e)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        val cb = callback ?: run {
            activePower = null
            return
        }
        callback = null
        activePower = null
        val context = appContext ?: return
        try {
            context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner?.stopScan(cb)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan failed", e)
        }
    }

    private fun handle(context: Context, result: ScanResult) {
        val data = result.scanRecord?.getManufacturerSpecificData(ProximityParser.APPLE_COMPANY_ID) ?: return
        PodsRepository.onAdvertisement(context, result.device.address, result.rssi, data, System.currentTimeMillis())
    }

    /** Matches Apple manufacturer data that starts with a proximity pairing message. */
    private fun filter(): ScanFilter {
        val data = ByteArray(27)
        val mask = ByteArray(27)
        data[0] = ProximityParser.TYPE_PROXIMITY_PAIRING.toByte()
        data[1] = ProximityParser.PROXIMITY_PAIRING_LENGTH.toByte()
        mask[0] = 0xFF.toByte()
        mask[1] = 0xFF.toByte()
        return ScanFilter.Builder()
            .setManufacturerData(ProximityParser.APPLE_COMPANY_ID, data, mask)
            .build()
    }
}
