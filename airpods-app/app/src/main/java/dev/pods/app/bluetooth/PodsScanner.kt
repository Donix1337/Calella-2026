package dev.pods.app.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import dev.pods.app.ble.ProximityParser
import dev.pods.app.data.PodsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ScanPower { BALANCED, HIGH }

/**
 * How we ask the Bluetooth controller for results. Some phones' hardware filters
 * silently drop AirPods packets, so we fall back to broader filters and finally
 * to filtering in software.
 */
enum class ScanStrategy(val label: String) {
    PROXIMITY_FILTER("Hardware filter"),
    APPLE_FILTER("Apple filter"),
    SOFTWARE_FILTER("Software filter"),
}

data class ScanDiagnostics(
    val status: String = "Not started",
    val strategy: ScanStrategy = ScanStrategy.PROXIMITY_FILTER,
    val appleSeen: Int = 0,
    /** Any proximity pairing message, readable or encrypted. */
    val podsSeen: Int = 0,
    /** Readable battery messages. */
    val batterySeen: Int = 0,
    val lastPacket: String? = null,
    val lastOtherPacket: String? = null,
    val lastRssi: Int? = null,
    val lastPacketAt: Long = 0L,
    val offloadedFiltering: Boolean? = null,
    val offloadedBatching: Boolean? = null,
)

/**
 * One shared BLE scan for the whole app. The UI and the background service each
 * register a request; the scan runs at the strongest requested power. Android
 * throttles apps that restart scans too often, so we only restart when the
 * effective settings actually change.
 */
object PodsScanner {
    private const val TAG = "PodsScanner"
    private const val PREFS = "pods_scanner"
    private const val KEY_STRATEGY = "strategy_v2"
    private const val ESCALATE_AFTER_MS = 8_000L

    private val requests = HashMap<String, ScanPower>()
    private val handler = Handler(Looper.getMainLooper())
    private var appContext: Context? = null
    private var callback: ScanCallback? = null
    private var activePower: ScanPower? = null
    private var strategy: ScanStrategy? = null
    private var startedAt = 0L
    private var podsSinceStart = 0

    private val _diagnostics = MutableStateFlow(ScanDiagnostics())
    val diagnostics: StateFlow<ScanDiagnostics> = _diagnostics.asStateFlow()
    private var lastPublish = 0L
    private var appleSeen = 0
    private var podsSeen = 0
    private var batterySeen = 0
    private var lastPacket: String? = null
    private var lastOtherPacket: String? = null
    private var lastRssi: Int? = null
    private var lastPacketAt = 0L

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
        if (desired != null) startScan(desired) else publish(status = "Idle", force = true)
    }

    private fun currentStrategy(context: Context): ScanStrategy {
        strategy?.let { return it }
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_STRATEGY, null)
        return (saved?.let { runCatching { ScanStrategy.valueOf(it) }.getOrNull() } ?: ScanStrategy.PROXIMITY_FILTER)
            .also { strategy = it }
    }

    @SuppressLint("MissingPermission")
    private fun startScan(power: ScanPower) {
        val context = appContext ?: return
        if (!Permissions.hasBluetooth(context)) return publish(status = "No Nearby devices permission", force = true)
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        if (adapter == null || !adapter.isEnabled) return publish(status = "Bluetooth is off", force = true)
        val scanner = adapter.bluetoothLeScanner ?: return publish(status = "Scanner unavailable", force = true)
        val mode = currentStrategy(context)

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
                publish(status = "Failed (error $errorCode)", force = true)
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(
                if (power == ScanPower.HIGH) ScanSettings.SCAN_MODE_LOW_LATENCY else ScanSettings.SCAN_MODE_BALANCED
            )
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()

        val filters = when (mode) {
            ScanStrategy.PROXIMITY_FILTER -> listOf(proximityFilter())
            ScanStrategy.APPLE_FILTER -> listOf(appleFilter())
            ScanStrategy.SOFTWARE_FILTER -> emptyList()
        }

        try {
            scanner.startScan(filters, settings, cb)
            callback = cb
            activePower = power
            startedAt = SystemClock.elapsedRealtime()
            podsSinceStart = 0
            _diagnostics.value = _diagnostics.value.copy(
                offloadedFiltering = adapter.isOffloadedFilteringSupported,
                offloadedBatching = adapter.isOffloadedScanBatchingSupported,
            )
            publish(status = "Scanning" + if (power == ScanPower.HIGH) " (fast)" else "", force = true)
            scheduleEscalation()
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to scan", e)
            publish(status = "No Nearby devices permission", force = true)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Bluetooth not ready", e)
            publish(status = "Bluetooth not ready", force = true)
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        handler.removeCallbacks(escalate)
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

    private fun scheduleEscalation() {
        handler.removeCallbacks(escalate)
        handler.postDelayed(escalate, ESCALATE_AFTER_MS)
    }

    /**
     * Connected AirPods that are out of the case advertise several times a
     * second. If we hear nothing, the current filter isn't working on this phone.
     */
    private val escalate: Runnable = Runnable {
        synchronized(PodsScanner) {
            if (callback == null) return@synchronized
            val connected = PodsRepository.state.value.isConnected
            val quietFor = SystemClock.elapsedRealtime() - startedAt
            val current = strategy ?: return@synchronized
            if (connected && podsSinceStart == 0 && quietFor >= ESCALATE_AFTER_MS) {
                val next = ScanStrategy.entries.getOrNull(current.ordinal + 1)
                if (next != null) {
                    Log.i(TAG, "No AirPods packets with $current, trying $next")
                    strategy = next
                    apply(force = true)
                    return@synchronized
                }
            }
            if (podsSinceStart == 0) scheduleEscalation()
        }
    }

    private fun handle(context: Context, result: ScanResult) {
        val data = result.scanRecord?.getManufacturerSpecificData(ProximityParser.APPLE_COMPANY_ID) ?: return
        appleSeen++
        if (!ProximityParser.isFromAirPods(data)) return publish()
        podsSeen++
        podsSinceStart++
        if (podsSinceStart == 1) rememberWorkingStrategy(context)
        val readable = ProximityParser.parse(data) != null
        val hex = data.joinToString(" ") { "%02X".format(it) }
        if (readable) {
            batterySeen++
            lastPacket = hex
            lastRssi = result.rssi
            lastPacketAt = System.currentTimeMillis()
        } else {
            lastOtherPacket = hex
        }
        publish()
        if (readable) {
            PodsRepository.onAdvertisement(context, result.device.address, result.rssi, data, System.currentTimeMillis())
        }
    }

    private fun rememberWorkingStrategy(context: Context) {
        val s = strategy ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_STRATEGY, s.name).apply()
    }

    private fun publish(status: String? = null, force: Boolean = false) {
        val now = SystemClock.elapsedRealtime()
        if (!force && now - lastPublish < 500) return
        lastPublish = now
        val current = _diagnostics.value
        _diagnostics.value = current.copy(
            status = status ?: current.status,
            strategy = strategy ?: current.strategy,
            appleSeen = appleSeen,
            podsSeen = podsSeen,
            batterySeen = batterySeen,
            lastPacket = lastPacket,
            lastOtherPacket = lastOtherPacket,
            lastRssi = lastRssi,
            lastPacketAt = lastPacketAt,
        )
    }

    /** Apple manufacturer data starting with a proximity pairing message of any length. */
    private fun proximityFilter(): ScanFilter = ScanFilter.Builder()
        .setManufacturerData(
            ProximityParser.APPLE_COMPANY_ID,
            byteArrayOf(ProximityParser.TYPE_PROXIMITY_PAIRING.toByte()),
            byteArrayOf(0xFF.toByte()),
        )
        .build()

    /** Any Apple manufacturer data. */
    private fun appleFilter(): ScanFilter = ScanFilter.Builder()
        .setManufacturerData(ProximityParser.APPLE_COMPANY_ID, ByteArray(0))
        .build()
}
