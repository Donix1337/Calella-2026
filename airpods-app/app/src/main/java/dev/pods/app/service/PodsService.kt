package dev.pods.app.service

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.pods.app.bluetooth.BtConnections
import dev.pods.app.bluetooth.Permissions
import dev.pods.app.bluetooth.PodsScanner
import dev.pods.app.bluetooth.ScanPower
import dev.pods.app.data.PodsRepository
import dev.pods.app.data.PodsSettings
import dev.pods.app.data.PodsSnapshot
import dev.pods.app.data.PodsState
import dev.pods.app.popup.PopupController
import dev.pods.app.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Runs while AirPods are connected: keeps scanning for their battery broadcasts,
 * drives the notification, ear detection, low battery alerts, the connection
 * pop-up and widget refreshes.
 */
class PodsService : Service() {

    companion object {
        private const val TAG = "PodsService"
        private const val SCAN_CLIENT = "service"
        private const val LOW_BATTERY = 20

        fun start(context: Context) {
            if (!Permissions.hasBluetooth(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, PodsService::class.java))
            } catch (e: Exception) {
                // Android refused a background start; the app will retry when opened.
                Log.w(TAG, "Could not start service", e)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PodsService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var earDetector: EarDetector
    private lateinit var popup: PopupController
    private var started = false
    private var stopJob: Job? = null
    private var lastNotificationText: String? = null
    private var lastConnectedAddress: String? = null
    private var popupPendingSince = 0L
    private val lowBatteryWarned = mutableSetOf<String>()

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (PodsRepository.refreshPhoneBattery(context)) WidgetUpdater.request(context)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        PodsSettings.init(this)
        PodsRepository.init(this)
        earDetector = EarDetector(this)
        popup = PopupController(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            if (!goForeground()) {
                stopSelf()
                return START_NOT_STICKY
            }
            started = true
            begin()
        }
        return START_STICKY
    }

    private fun goForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this,
            Notifications.STATUS_ID,
            Notifications.status(this, PodsRepository.state.value),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        true
    } catch (e: Exception) {
        Log.w(TAG, "startForeground failed", e)
        false
    }

    private fun begin() {
        ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        BtConnections.refresh(this)

        scope.launch {
            PodsSettings.values.collect { settings ->
                val power = if (settings.earDetection) ScanPower.HIGH else ScanPower.BALANCED
                PodsScanner.request(this@PodsService, SCAN_CLIENT, power)
                if (!settings.backgroundUpdates) stopSelf()
            }
        }
        scope.launch {
            PodsRepository.state.collect { onState(it) }
        }
        scope.launch {
            // Lets ear detection settle even when advertisements are sparse.
            while (isActive) {
                delay(400)
                val state = PodsRepository.state.value
                if (state.isConnected) {
                    earDetector.tick(state.snapshot, PodsSettings.current, System.currentTimeMillis())
                }
                maybeShowPopup(state)
            }
        }
    }

    private fun onState(state: PodsState) {
        val now = System.currentTimeMillis()

        val text = state.deviceName + Notifications.statusText(state)
        if (text != lastNotificationText) {
            lastNotificationText = text
            Notifications.notifyStatus(this, state)
        }

        val connected = state.connected
        if (connected == null) {
            lastConnectedAddress = null
            popupPendingSince = 0L
            scheduleStop()
        } else {
            stopJob?.cancel()
            stopJob = null
            if (connected.address != lastConnectedAddress) {
                lastConnectedAddress = connected.address
                earDetector.reset()
                lowBatteryWarned.clear()
                popupPendingSince = now
            }
        }

        val snapshot = state.snapshot ?: return
        if (connected != null) {
            earDetector.onSnapshot(snapshot, PodsSettings.current, now)
            checkLowBattery(state, snapshot)
        }
        maybeShowPopup(state)
    }

    private fun maybeShowPopup(state: PodsState) {
        if (popupPendingSince == 0L) return
        val now = System.currentTimeMillis()
        if (now - popupPendingSince > 15_000) {
            popupPendingSince = 0L
            return
        }
        val snapshot = state.snapshot ?: return
        // Wait for a fresh reading from this connection before showing numbers.
        if (snapshot.updatedAt < popupPendingSince - 2_000 && PodsRepository.signal.value.lastSeen < popupPendingSince) return
        popupPendingSince = 0L
        if (PodsSettings.current.popup && Permissions.canDrawOverlays(this)) popup.show()
    }

    private fun checkLowBattery(state: PodsState, s: PodsSnapshot) {
        if (!PodsSettings.current.lowBatteryAlert) return
        val parts = if (s.isHeadphones) {
            listOf(Triple("headphones", s.single, s.singleCharging))
        } else {
            listOf(
                Triple("left", s.left.takeIf { !s.leftInCase }, s.leftCharging),
                Triple("right", s.right.takeIf { !s.rightInCase }, s.rightCharging),
            )
        }
        for ((key, level, charging) in parts) {
            if (level == null) continue
            if (charging || level > LOW_BATTERY + 10) {
                lowBatteryWarned.remove(key)
                continue
            }
            if (level <= LOW_BATTERY && lowBatteryWarned.add(key)) {
                val what = when (key) {
                    "left" -> "Left AirPod"
                    "right" -> "Right AirPod"
                    else -> state.deviceName
                }
                Notifications.notifyLowBattery(this, "Battery low", "$what is at $level%.")
            }
        }
    }

    private fun scheduleStop() {
        if (stopJob?.isActive == true) return
        stopJob = scope.launch {
            // AirPods often drop and reconnect when switching; give them a moment.
            delay(20_000)
            if (PodsRepository.state.value.connected == null) stopSelf()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        PodsScanner.release(SCAN_CLIENT)
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: IllegalArgumentException) {
            // Never registered.
        }
        popup.dismiss(animated = false)
        WidgetUpdater.request(this, immediate = true)
        super.onDestroy()
    }
}
