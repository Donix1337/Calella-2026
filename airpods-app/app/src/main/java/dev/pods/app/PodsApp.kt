package dev.pods.app

import android.app.Application
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.pods.app.bluetooth.PodsScanner
import dev.pods.app.data.PodsRepository
import dev.pods.app.data.PodsSettings
import dev.pods.app.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class PodsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        PodsSettings.init(this)
        PodsRepository.init(this)
        Notifications.createChannels(this)
        ContextCompat.registerReceiver(
            this,
            BluetoothStateReceiver(),
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    /** Scans die with the radio, so restart them when Bluetooth comes back on. */
    private class BluetoothStateReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)) {
                BluetoothAdapter.STATE_ON -> PodsScanner.restart()
                BluetoothAdapter.STATE_OFF -> PodsRepository.setConnected(context, null)
            }
        }
    }

    companion object {
        /** Process-wide scope for fire-and-forget work such as widget refreshes. */
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
