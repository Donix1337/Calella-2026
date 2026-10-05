package dev.pods.app.data

import dev.pods.app.ble.PodKind
import dev.pods.app.ble.PodModel
import dev.pods.app.ble.ProximityMessage

data class DeviceInfo(val name: String, val address: String)

data class PhoneBattery(val level: Int = 0, val charging: Boolean = false)

/** The latest battery and wear state we decoded for the user's AirPods. */
data class PodsSnapshot(
    val modelId: Int,
    val left: Int?,
    val right: Int?,
    val case: Int?,
    val leftCharging: Boolean,
    val rightCharging: Boolean,
    val caseCharging: Boolean,
    val leftInEar: Boolean,
    val rightInEar: Boolean,
    val leftInCase: Boolean,
    val rightInCase: Boolean,
    val single: Int?,
    val singleCharging: Boolean,
    /** True when the case level is remembered from earlier, not live. */
    val caseFromMemory: Boolean = false,
    val updatedAt: Long,
) {
    val model: PodModel? get() = PodModel.fromId(modelId)
    val kind: PodKind get() = model?.kind ?: PodKind.PRO
    val isHeadphones: Boolean get() = kind == PodKind.HEADPHONES
    val inEarCount: Int get() = (if (leftInEar) 1 else 0) + (if (rightInEar) 1 else 0)

    fun sameReading(other: PodsSnapshot): Boolean =
        copy(updatedAt = 0) == other.copy(updatedAt = 0)

    companion object {
        fun from(msg: ProximityMessage, now: Long) = PodsSnapshot(
            modelId = msg.modelId,
            left = msg.left,
            right = msg.right,
            case = msg.case,
            leftCharging = msg.leftCharging,
            rightCharging = msg.rightCharging,
            caseCharging = msg.caseCharging,
            leftInEar = msg.leftInEar,
            rightInEar = msg.rightInEar,
            leftInCase = msg.leftInCase,
            rightInCase = msg.rightInCase,
            single = msg.single,
            singleCharging = msg.singleCharging,
            updatedAt = now,
        )
    }
}

data class PodsState(
    val snapshot: PodsSnapshot? = null,
    /** The AirPods currently connected over Bluetooth audio, if any. */
    val connected: DeviceInfo? = null,
    /** The last AirPods we saw connected, kept for naming when disconnected. */
    val lastDevice: DeviceInfo? = null,
    val phone: PhoneBattery = PhoneBattery(),
    /** Overall level Android gets from the AirPods over the headset profile, if any. */
    val headsetBattery: Int? = null,
) {
    val isConnected: Boolean get() = connected != null

    val deviceName: String
        get() = connected?.name ?: lastDevice?.name ?: modelName

    val modelName: String
        get() = snapshot?.model?.displayName
            ?: if ((connected ?: lastDevice)?.name?.contains("Pro", ignoreCase = true) == true) "AirPods Pro" else "AirPods"

    /** Product name for compact places like the widget. */
    val shortModelName: String
        get() = snapshot?.model?.displayName ?: modelName
}

/** Live radio info, updated far more often than [PodsState]. */
data class Signal(val rssi: Int? = null, val lastSeen: Long = 0L)
