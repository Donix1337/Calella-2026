package dev.pods.app.aap

/** One component's battery from an AAP battery notification, in exact percent. */
data class AapLevel(val level: Int, val charging: Boolean)

data class AapBattery(
    val left: AapLevel?,
    val right: AapLevel?,
    val case: AapLevel?,
    /** Single battery for headphones such as AirPods Max. */
    val single: AapLevel?,
)

enum class PodPlacement { IN_EAR, OUT_OF_EAR, IN_CASE, UNKNOWN }

/** Ear detection reports the primary and secondary bud, not left and right. */
data class AapEar(val primary: PodPlacement, val secondary: PodPlacement) {
    val inEarCount: Int get() = listOf(primary, secondary).count { it == PodPlacement.IN_EAR }
    val bothInCase: Boolean get() = primary == PodPlacement.IN_CASE && secondary == PodPlacement.IN_CASE
}

object AapPackets {
    private const val COMPONENT_SINGLE = 0x01
    private const val COMPONENT_RIGHT = 0x02
    private const val COMPONENT_LEFT = 0x04
    private const val COMPONENT_CASE = 0x08
    private const val STATUS_CHARGING = 0x01
    private const val STATUS_DISCONNECTED = 0x04

    /**
     * `04 00 04 00 04 00 [count] ([component] 01 [level] [status] 01) × count`
     */
    fun parseBattery(packet: ByteArray): AapBattery? {
        if (packet.size < 7 || packet.u(4) != 0x04) return null
        val count = packet.u(6)
        var left: AapLevel? = null
        var right: AapLevel? = null
        var case: AapLevel? = null
        var single: AapLevel? = null
        for (i in 0 until count) {
            val at = 7 + i * 5
            if (at + 3 >= packet.size) break
            val component = packet.u(at)
            val level = packet.u(at + 2)
            val status = packet.u(at + 3)
            if (status == STATUS_DISCONNECTED || level > 100) continue
            val value = AapLevel(level, status == STATUS_CHARGING)
            when (component) {
                COMPONENT_LEFT -> left = value
                COMPONENT_RIGHT -> right = value
                COMPONENT_CASE -> case = value
                COMPONENT_SINGLE -> single = value
            }
        }
        if (left == null && right == null && case == null && single == null) return null
        return AapBattery(left, right, case, single)
    }

    /** `04 00 04 00 06 00 [primary] [secondary]`: 00 in ear, 01 out of ear, 02 in case. */
    fun parseEar(packet: ByteArray): AapEar? {
        if (packet.size < 8 || packet.u(4) != 0x06) return null
        return AapEar(placement(packet.u(6)), placement(packet.u(7)))
    }

    private fun placement(value: Int) = when (value) {
        0x00 -> PodPlacement.IN_EAR
        0x01 -> PodPlacement.OUT_OF_EAR
        0x02 -> PodPlacement.IN_CASE
        else -> PodPlacement.UNKNOWN
    }

    private fun ByteArray.u(index: Int): Int = this[index].toInt() and 0xFF
}
