package dev.pods.app.aap

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import dev.pods.app.PodsApp
import dev.pods.app.bluetooth.Permissions
import dev.pods.app.data.PodsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.IOException

/**
 * Talks to AirPods over Apple's accessory protocol (AAP): an L2CAP channel on
 * PSM 0x1001 that Apple devices use for exact battery, ear detection and noise
 * control. Many Android builds can't open it (a Bluetooth stack bug fixed in
 * Android 17), so this is tried opportunistically and the app falls back to
 * BLE broadcasts and the system battery level when it fails.
 *
 * Packet formats follow the LibrePods project's protocol notes.
 */
object AapClient {
    private const val TAG = "AapClient"
    private const val PSM = 0x1001
    private const val TYPE_L2CAP = 3
    private val UUID: ParcelUuid = ParcelUuid.fromString("74ec2172-0bad-4d01-8f77-997b2be0722a")

    private val HANDSHAKE = bytes(0x00, 0x00, 0x04, 0x00, 0x01, 0x00, 0x02, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val SET_FEATURES = bytes(0x04, 0x00, 0x04, 0x00, 0x4D, 0x00, 0xD7, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
    private val REQUEST_NOTIFICATIONS = bytes(0x04, 0x00, 0x04, 0x00, 0x0F, 0x00, 0xFF, 0xFF, 0xFF, 0xFF)

    private const val OP_BATTERY = 0x04
    private const val OP_EAR = 0x06
    private const val OP_CONTROL = 0x09
    private const val CONTROL_LISTENING_MODE = 0x0D

    enum class State { IDLE, CONNECTING, CONNECTED, FAILED }

    data class Status(
        val state: State = State.IDLE,
        val detail: String = "",
        /** 1 off, 2 noise cancellation, 3 transparency, 4 adaptive. */
        val noiseMode: Int? = null,
        val packets: Int = 0,
        val lastPacket: String? = null,
        val address: String? = null,
    ) {
        val isConnected: Boolean get() = state == State.CONNECTED
    }

    private val _status = MutableStateFlow(Status())
    val status: StateFlow<Status> = _status.asStateFlow()

    @Volatile
    private var socket: BluetoothSocket? = null
    private var job: Job? = null

    /** Opens the channel if it isn't already open or being opened for [address]. */
    @Synchronized
    fun connect(context: Context, address: String) {
        val current = _status.value
        if (current.address == address && (current.state == State.CONNECTING || current.state == State.CONNECTED)) return
        disconnect()
        val app = context.applicationContext
        _status.value = Status(State.CONNECTING, "Connecting…", address = address)
        job = PodsApp.scope.launch(Dispatchers.IO) { run(app, address) }
    }

    @Synchronized
    fun disconnect() {
        job?.cancel()
        job = null
        closeSocket()
        _status.update { if (it.state == State.FAILED) it else Status() }
    }

    /** 1 off, 2 noise cancellation, 3 transparency, 4 adaptive. */
    fun setNoiseMode(mode: Int) {
        val ok = send(bytes(0x04, 0x00, 0x04, 0x00, OP_CONTROL, 0x00, CONTROL_LISTENING_MODE, mode, 0x00, 0x00, 0x00))
        if (ok) _status.update { it.copy(noiseMode = mode) }
    }

    @SuppressLint("MissingPermission")
    private suspend fun run(context: Context, address: String) {
        if (!Permissions.hasBluetooth(context)) return fail(address, "No Nearby devices permission")
        val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return fail(address, "No Bluetooth adapter")
        val device = try {
            adapter.getRemoteDevice(address)
        } catch (e: IllegalArgumentException) {
            return fail(address, "Bad address")
        }

        var sock: BluetoothSocket? = null
        var lastError = "unknown"
        for (secure in listOf(true, false)) {
            val candidate = try {
                createSocket(adapter, device, secure)
            } catch (e: Throwable) {
                lastError = "Can't create socket: ${e.javaClass.simpleName}"
                continue
            }
            socket = candidate
            // BluetoothSocket.connect() blocks without a timeout; closing the socket unblocks it.
            val watchdog = PodsApp.scope.launch {
                delay(8_000)
                if (_status.value.state == State.CONNECTING) closeSocket()
            }
            try {
                candidate.connect()
                sock = candidate
            } catch (e: IOException) {
                lastError = "Connection refused (${e.message ?: "IOException"})"
                closeSocket()
            } finally {
                watchdog.cancel()
            }
            if (sock != null) break
        }
        if (sock == null) return fail(address, lastError)

        try {
            val output = sock.outputStream
            output.write(HANDSHAKE)
            output.flush()
            delay(150)
            output.write(SET_FEATURES)
            output.flush()
            delay(150)
            output.write(REQUEST_NOTIFICATIONS)
            output.flush()
        } catch (e: IOException) {
            closeSocket()
            return fail(address, "Handshake failed (${e.message})")
        }

        _status.update { it.copy(state = State.CONNECTED, detail = "Connected", address = address) }
        Log.i(TAG, "AAP connected")

        val input = sock.inputStream
        val buffer = ByteArray(1024)
        try {
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                if (n > 0) handle(context, buffer.copyOf(n))
            }
        } catch (e: IOException) {
            Log.i(TAG, "AAP closed: ${e.message}")
        } finally {
            closeSocket()
            _status.update {
                if (it.state == State.CONNECTED) it.copy(state = State.IDLE, detail = "Disconnected") else it
            }
        }
    }

    private fun handle(context: Context, packet: ByteArray) {
        _status.update { s ->
            s.copy(packets = s.packets + 1, lastPacket = packet.joinToString(" ") { "%02X".format(it) })
        }
        if (packet.size < 6 || packet.u(0) != 0x04 || packet.u(1) != 0x00 || packet.u(2) != 0x04 || packet.u(3) != 0x00) return
        when (packet.u(4)) {
            OP_BATTERY -> AapPackets.parseBattery(packet)?.let { PodsRepository.onAapBattery(context, it) }
            OP_EAR -> AapPackets.parseEar(packet)?.let { PodsRepository.onAapEar(context, it) }
            OP_CONTROL -> if (packet.size >= 8 && packet.u(6) == CONTROL_LISTENING_MODE) {
                _status.update { it.copy(noiseMode = packet.u(7)) }
            }
        }
    }

    private fun send(data: ByteArray): Boolean {
        val sock = socket ?: return false
        if (!_status.value.isConnected) return false
        return try {
            sock.outputStream.write(data)
            sock.outputStream.flush()
            true
        } catch (e: IOException) {
            false
        }
    }

    private fun fail(address: String, reason: String) {
        Log.w(TAG, reason)
        _status.value = Status(State.FAILED, reason, address = address)
    }

    private fun closeSocket() {
        val sock = socket ?: return
        socket = null
        try {
            sock.close()
        } catch (e: IOException) {
            // Already closed.
        }
    }

    /**
     * BluetoothSocket has no public L2CAP-over-BR/EDR constructor, and its hidden
     * one changed shape across Android versions; try each known signature.
     */
    private fun createSocket(adapter: BluetoothAdapter, device: BluetoothDevice, secure: Boolean): BluetoothSocket {
        HiddenApiBypass.addHiddenApiExemptions("Landroid/bluetooth/BluetoothSocket;")
        val s = secure
        val specs: List<Array<Any>> = listOf(
            arrayOf(adapter, device, TYPE_L2CAP, s, s, PSM, UUID),
            arrayOf(device, TYPE_L2CAP, s, s, PSM, UUID),
            arrayOf(device, TYPE_L2CAP, 1, s, s, PSM, UUID),
            arrayOf(TYPE_L2CAP, 1, s, s, device, PSM, UUID),
            arrayOf(TYPE_L2CAP, s, s, device, PSM, UUID),
        )
        var last: Throwable? = null
        for (args in specs) {
            try {
                val types = args.map { arg ->
                    when (arg) {
                        is Int -> Int::class.javaPrimitiveType!!
                        is Boolean -> Boolean::class.javaPrimitiveType!!
                        is BluetoothAdapter -> BluetoothAdapter::class.java
                        is BluetoothDevice -> BluetoothDevice::class.java
                        is ParcelUuid -> ParcelUuid::class.java
                        else -> arg.javaClass
                    }
                }.toTypedArray()
                val constructor = BluetoothSocket::class.java.getDeclaredConstructor(*types)
                constructor.isAccessible = true
                return constructor.newInstance(*args) as BluetoothSocket
            } catch (e: Throwable) {
                last = e
            }
        }
        throw last ?: IllegalStateException("No BluetoothSocket constructor")
    }

    private fun ByteArray.u(index: Int): Int = this[index].toInt() and 0xFF

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }
}
