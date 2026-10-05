package dev.pods.app.data

import android.content.Context
import android.content.SharedPreferences
import android.os.BatteryManager
import dev.pods.app.ble.CandidateTracker
import dev.pods.app.ble.ProximityParser
import dev.pods.app.widget.WidgetUpdater
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import kotlin.math.abs

/**
 * Single source of truth for AirPods state, shared by the app UI, the
 * background service, the notification and the home screen widget.
 */
object PodsRepository {
    private const val PREFS = "pods_state"
    private const val KEY_STATE = "state"

    private lateinit var prefs: SharedPreferences
    private val tracker = CandidateTracker()

    private val _state = MutableStateFlow(PodsState())
    val state: StateFlow<PodsState> = _state.asStateFlow()

    private val _signal = MutableStateFlow(Signal())
    val signal: StateFlow<Signal> = _signal.asStateFlow()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            _state.value = load().copy(phone = readPhoneBattery(context))
            initialized = true
        }
    }

    fun onAdvertisement(context: Context, address: String, rssi: Int, data: ByteArray, now: Long) {
        val message = ProximityParser.parse(data) ?: return
        val connected = _state.value.isConnected
        // Be pickier when not connected so a stranger's AirPods across the room don't show up.
        val minRssi = if (connected) -90 else -75
        if (!tracker.offer(address, rssi, now, minRssi)) return

        val previousSignal = _signal.value
        if (now - previousSignal.lastSeen > 1_000 || abs((previousSignal.rssi ?: 0) - rssi) >= 4) {
            _signal.value = Signal(rssi, now)
        }

        val previous = _state.value.snapshot
        var snapshot = PodsSnapshot.from(message, now)
        // The case only reports while a bud is inside it; remember its last level.
        if (snapshot.case == null && !snapshot.isHeadphones && previous?.case != null) {
            snapshot = snapshot.copy(case = previous.case, caseCharging = false, caseFromMemory = true)
        }
        if (previous != null && previous.sameReading(snapshot)) return

        _state.update { it.copy(snapshot = snapshot) }
        save()
        WidgetUpdater.request(context)
    }

    fun setConnected(context: Context, device: DeviceInfo?) {
        val current = _state.value
        if (current.connected == device) return
        if (device != null && current.connected?.address != device.address) tracker.reset()
        _state.update { it.copy(connected = device, lastDevice = device ?: it.lastDevice) }
        save()
        WidgetUpdater.request(context, immediate = true)
    }

    fun refreshPhoneBattery(context: Context): Boolean {
        val phone = readPhoneBattery(context)
        if (phone == _state.value.phone) return false
        _state.update { it.copy(phone = phone) }
        return true
    }

    private fun readPhoneBattery(context: Context): PhoneBattery {
        val manager = context.getSystemService(BatteryManager::class.java) ?: return PhoneBattery()
        val level = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).coerceIn(0, 100)
        return PhoneBattery(level, manager.isCharging)
    }

    private fun save() {
        if (!initialized) return
        val s = _state.value
        val json = JSONObject()
        s.snapshot?.let { json.put("snapshot", it.toJson()) }
        s.lastDevice?.let { json.put("lastDevice", JSONObject().put("name", it.name).put("address", it.address)) }
        json.put("lastSeen", _signal.value.lastSeen)
        prefs.edit().putString(KEY_STATE, json.toString()).apply()
    }

    private fun load(): PodsState {
        val raw = prefs.getString(KEY_STATE, null) ?: return PodsState()
        return try {
            val json = JSONObject(raw)
            val snapshot = json.optJSONObject("snapshot")?.let { snapshotFromJson(it) }
            val last = json.optJSONObject("lastDevice")?.let {
                DeviceInfo(it.getString("name"), it.getString("address"))
            }
            _signal.value = Signal(null, json.optLong("lastSeen", 0L))
            // We can't know whether the AirPods are still connected after a restart;
            // the Bluetooth receiver and the app re-check that.
            PodsState(snapshot = snapshot, lastDevice = last)
        } catch (e: Exception) {
            PodsState()
        }
    }

    private fun PodsSnapshot.toJson(): JSONObject = JSONObject()
        .put("modelId", modelId)
        .put("left", left ?: -1)
        .put("right", right ?: -1)
        .put("case", case ?: -1)
        .put("leftCharging", leftCharging)
        .put("rightCharging", rightCharging)
        .put("caseCharging", caseCharging)
        .put("leftInEar", leftInEar)
        .put("rightInEar", rightInEar)
        .put("leftInCase", leftInCase)
        .put("rightInCase", rightInCase)
        .put("single", single ?: -1)
        .put("singleCharging", singleCharging)
        .put("updatedAt", updatedAt)

    private fun snapshotFromJson(j: JSONObject): PodsSnapshot {
        fun level(key: String) = j.optInt(key, -1).takeIf { it >= 0 }
        return PodsSnapshot(
            modelId = j.optInt("modelId"),
            left = level("left"),
            right = level("right"),
            case = level("case"),
            // Nothing is charging or in an ear as far as we know after a restart.
            leftCharging = false,
            rightCharging = false,
            caseCharging = false,
            leftInEar = false,
            rightInEar = false,
            leftInCase = j.optBoolean("leftInCase"),
            rightInCase = j.optBoolean("rightInCase"),
            single = level("single"),
            singleCharging = false,
            caseFromMemory = true,
            updatedAt = j.optLong("updatedAt"),
        )
    }
}
