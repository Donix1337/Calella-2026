package dev.pods.app.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AapPacketsTest {

    private fun hex(s: String): ByteArray =
        s.replace(" ", "").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test
    fun parsesBatteryNotification() {
        // Example from the LibrePods protocol notes (AirPods Pro 2).
        val battery = AapPackets.parseBattery(hex("04 00 04 00 04 00 03 02 01 64 02 01 04 01 63 01 01 08 01 11 02 01"))!!
        assertEquals(AapLevel(100, false), battery.right)
        assertEquals(AapLevel(99, true), battery.left)
        assertEquals(AapLevel(17, false), battery.case)
        assertNull(battery.single)
    }

    @Test
    fun skipsDisconnectedComponents() {
        val battery = AapPackets.parseBattery(hex("04 00 04 00 04 00 02 02 01 50 02 01 08 01 00 04 01"))!!
        assertEquals(80, battery.right?.level)
        assertNull(battery.case)
    }

    @Test
    fun parsesEarDetection() {
        val ear = AapPackets.parseEar(hex("04 00 04 00 06 00 00 01"))!!
        assertEquals(PodPlacement.IN_EAR, ear.primary)
        assertEquals(PodPlacement.OUT_OF_EAR, ear.secondary)
        assertEquals(1, ear.inEarCount)
        assertFalse(ear.bothInCase)
        assertTrue(AapPackets.parseEar(hex("04 00 04 00 06 00 02 02"))!!.bothInCase)
    }

    @Test
    fun rejectsOtherPackets() {
        assertNull(AapPackets.parseBattery(hex("04 00 04 00 06 00 00 01")))
        assertNull(AapPackets.parseEar(hex("04 00 04 00 04 00 01")))
        assertNull(AapPackets.parseBattery(hex("04 00 04 00 04 00 01 02 01")))
    }
}
