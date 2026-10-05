package dev.pods.app.ble

/**
 * One decoded "proximity pairing" message. AirPods broadcast this over BLE as
 * Apple manufacturer data (company 0x004C) several times a second while out of
 * the case, and whenever the case lid is open.
 *
 * Battery values are 0-100 in steps of 10, or null when the component isn't
 * reporting (out of range, or a bud asleep in a closed case).
 */
data class ProximityMessage(
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
    /** Battery for single-battery devices such as AirPods Max. */
    val single: Int?,
    val singleCharging: Boolean,
) {
    val model: PodModel? get() = PodModel.fromId(modelId)
    val isHeadphones: Boolean get() = model?.kind == PodKind.HEADPHONES
}

object ProximityParser {
    const val APPLE_COMPANY_ID = 0x004C
    const val TYPE_PROXIMITY_PAIRING = 0x07
    const val PROXIMITY_PAIRING_LENGTH = 0x19

    /**
     * Parses Apple manufacturer data (without the company ID). The payload can
     * hold several Continuity messages back to back as type/length/value, so we
     * look for the proximity pairing one rather than assuming it comes first.
     */
    fun parse(manufacturerData: ByteArray?): ProximityMessage? {
        val start = findProximityMessage(manufacturerData) ?: return null
        return parseMessage(manufacturerData!!, start)
    }

    /**
     * True for any proximity pairing message, including the short encrypted
     * variant (length 0x11) newer AirPods firmware sends while in use. Those
     * prove the AirPods are nearby but carry no readable battery data.
     */
    fun isFromAirPods(manufacturerData: ByteArray?): Boolean = findProximityMessage(manufacturerData) != null

    private fun findProximityMessage(data: ByteArray?): Int? {
        if (data == null) return null
        var i = 0
        while (i + 1 < data.size) {
            val type = data[i].u()
            val length = data[i + 1].u()
            if (type == TYPE_PROXIMITY_PAIRING) return i
            if (length == 0) return null
            i += 2 + length
        }
        return null
    }

    private fun parseMessage(data: ByteArray, start: Int): ProximityMessage? {
        // Only the full 25-byte status message carries battery in the clear;
        // the shorter variants are encrypted and decode to random numbers.
        if (data[start + 1].u() != PROXIMITY_PAIRING_LENGTH) return null
        if (data.size < start + 2 + PROXIMITY_PAIRING_LENGTH) return null
        fun at(offset: Int) = data[start + offset].u()

        // Apple audio product IDs are all 0x20xx.
        if (at(4) != 0x20) return null

        val modelId = (at(4) shl 8) or at(3)
        val status = at(5)
        val pods = at(6)
        val flagsAndCase = at(7)

        val flags = flagsAndCase shr 4
        val caseLevel = level(flagsAndCase and 0x0F)

        // Status bits, as worked out by the community (furiousMAC, OpenPods, CAPod).
        val leftIsPrimary = status.bit(5)
        val thisPodInCase = status.bit(6)
        val onePodInCase = status.bit(4)
        val bothPodsInCase = status.bit(2)

        val left = level(if (leftIsPrimary) pods and 0x0F else pods shr 4)
        val right = level(if (leftIsPrimary) pods shr 4 else pods and 0x0F)

        val leftCharging = if (leftIsPrimary) flags.bit(0) else flags.bit(1)
        val rightCharging = if (leftIsPrimary) flags.bit(1) else flags.bit(0)
        val caseCharging = flags.bit(2)

        // The in-ear bits swap meaning when the advertising pod sits in the case.
        val earBitsSwapped = leftIsPrimary xor thisPodInCase
        val leftInEar = if (earBitsSwapped) status.bit(1) else status.bit(3)
        val rightInEar = if (earBitsSwapped) status.bit(3) else status.bit(1)

        val primaryInCase = bothPodsInCase || (onePodInCase && thisPodInCase)
        val secondaryInCase = bothPodsInCase || (onePodInCase && !thisPodInCase)
        val leftInCase = if (leftIsPrimary) primaryInCase else secondaryInCase
        val rightInCase = if (leftIsPrimary) secondaryInCase else primaryInCase

        val single = level(pods and 0x0F) ?: level(pods shr 4)

        return ProximityMessage(
            modelId = modelId,
            left = left,
            right = right,
            case = caseLevel,
            leftCharging = leftCharging && left != null,
            rightCharging = rightCharging && right != null,
            caseCharging = caseCharging && caseLevel != null,
            leftInEar = leftInEar && !leftInCase,
            rightInEar = rightInEar && !rightInCase,
            leftInCase = leftInCase,
            rightInCase = rightInCase,
            single = single,
            singleCharging = flags.bit(0) || flags.bit(1),
        )
    }

    /** Battery nibble: 0-10 means 0-100%, 15 means "not available". */
    private fun level(nibble: Int): Int? = if (nibble in 0..10) nibble * 10 else null

    private fun Byte.u(): Int = toInt() and 0xFF

    private fun Int.bit(index: Int): Boolean = (this shr index) and 1 == 1
}
