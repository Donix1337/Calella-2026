package dev.pods.app.ble

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateTrackerTest {

    @Test
    fun followsStrongestAndIgnoresWeakSignals() {
        val tracker = CandidateTracker()
        assertFalse(tracker.offer("far", -95, now = 0, minRssi = -80))
        assertTrue(tracker.offer("mine", -55, now = 10, minRssi = -80))
        assertFalse(tracker.offer("other", -70, now = 20, minRssi = -80))
        assertTrue(tracker.offer("mine", -56, now = 30, minRssi = -80))
    }

    @Test
    fun switchesOnlyWhenClearlyStronger() {
        val tracker = CandidateTracker()
        assertTrue(tracker.offer("a", -60, now = 0, minRssi = -90))
        // Slightly stronger isn't enough to switch.
        assertFalse(tracker.offer("b", -57, now = 10, minRssi = -90))
        // Much stronger over a few packets wins.
        var switched = false
        for (t in 1..10) switched = tracker.offer("b", -35, now = 10L + t, minRssi = -90)
        assertTrue(switched)
    }

    @Test
    fun forgetsStaleAddresses() {
        val tracker = CandidateTracker(windowMs = 1_000)
        assertTrue(tracker.offer("old", -50, now = 0, minRssi = -90))
        // The old address rotated away; the new one takes over once the old one ages out.
        assertTrue(tracker.offer("new", -65, now = 5_000, minRssi = -90))
    }
}
