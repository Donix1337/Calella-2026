package dev.pods.app.bluetooth

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.IntentCompat
import dev.pods.app.data.PodsRepository
import dev.pods.app.data.PodsSettings
import dev.pods.app.service.PodsService

/** Starts monitoring when AirPods connect and stops when they disconnect. */
class BluetoothReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        PodsSettings.init(context)
        PodsRepository.init(context)
        val device = IntentCompat.getParcelableExtra(intent, BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)

        when (intent.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED -> {
                if (device == null || !BtConnections.isApple(device)) return
                PodsRepository.setConnected(context, BtConnections.info(device))
                if (PodsSettings.current.backgroundUpdates) PodsService.start(context)
            }

            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                val connected = PodsRepository.state.value.connected ?: return
                if (device == null || device.address == connected.address) {
                    PodsRepository.setConnected(context, null)
                }
            }
        }
    }
}
