package dev.pods.app.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityParserTest {

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    private val payloadTail = "00 05 11 22 33 44 55 66 77 88 99 AA BB CC DD EE FF 00"

    @Test
    fun airPodsProBothInEar() {
        val msg = ProximityParser.parse(hex("07 19 01 0E 20 2B 97 8F 01 $payloadTail"))!!
        assertEquals(PodModel.AIRPODS_PRO, msg.model)
        // Left is primary (bit 5), so the low nibble is the left bud.
        assertEquals(70, msg.left)
        assertEquals(90, msg.right)
        assertNull(msg.case)
        assertTrue(msg.leftInEar)
        assertTrue(msg.rightInEar)
        assertFalse(msg.leftInCase)
        assertFalse(msg.rightInCase)
        assertFalse(msg.caseCharging)
    }

    @Test
    fun airPodsPro2BothInChargingCase() {
        val msg = ProximityParser.parse(hex("07 19 01 14 20 55 A8 46 31 $payloadTail"))!!
        assertEquals(PodModel.AIRPODS_PRO_2, msg.model)
        // Right is primary, so the high nibble is the left bud.
        assertEquals(100, msg.left)
        assertEquals(80, msg.right)
        assertEquals(60, msg.case)
        assertTrue(msg.caseCharging)
        assertTrue(msg.leftInCase)
        assertTrue(msg.rightInCase)
        assertFalse(msg.leftInEar)
        assertFalse(msg.rightInEar)
    }

    @Test
    fun chargingFlagsFollowPrimaryPod() {
        // flags 0b0011: both buds charging, case not charging; case at 50%.
        val msg = ProximityParser.parse(hex("07 19 01 24 20 75 55 35 31 $payloadTail"))!!
        assertEquals(PodModel.AIRPODS_PRO_2_USB_C, msg.model)
        assertTrue(msg.leftCharging)
        assertTrue(msg.rightCharging)
        assertFalse(msg.caseCharging)
        assertEquals(50, msg.case)
    }

    @Test
    fun unknownBatteryIsNull() {
        val msg = ProximityParser.parse(hex("07 19 01 0E 20 2B F9 FF 01 $payloadTail"))!!
        assertEquals(90, msg.left)
        assertNull(msg.right)
        assertNull(msg.case)
        assertFalse(msg.rightCharging)
    }

    @Test
    fun findsProximityMessageAfterOtherContinuityData() {
        val msg = ProximityParser.parse(hex("10 02 0B 1C 07 19 01 0E 20 2B 97 8F 01 $payloadTail"))!!
        assertEquals(70, msg.left)
    }

    @Test
    fun headphonesReportSingleBattery() {
        val msg = ProximityParser.parse(hex("07 19 01 0A 20 01 F6 1F 01 $payloadTail"))!!
        assertTrue(msg.isHeadphones)
        assertEquals(60, msg.single)
        assertTrue(msg.singleCharging)
    }

    @Test
    fun rejectsOtherMessagesAndGarbage() {
        assertNull(ProximityParser.parse(null))
        assertNull(ProximityParser.parse(byteArrayOf()))
        assertNull(ProximityParser.parse(hex("02 15 00 11 22 33")))
        assertNull(ProximityParser.parse(hex("07 19 01 0E")))
    }
}
