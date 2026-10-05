package dev.pods.app.ble

/**
 * AirPods advertise from a random address that rotates every few minutes, and
 * other people's AirPods broadcast the same kind of packets. We follow the
 * strongest nearby signal and only switch when another one is clearly stronger,
 * so the numbers don't flicker between two pairs.
 */
class CandidateTracker(
    private val windowMs: Long = 10_000,
    private val switchMarginDb: Double = 8.0,
) {
    private class Candidate(var rssi: Double, var lastSeen: Long)

    private val candidates = HashMap<String, Candidate>()
    private var selected: String? = null

    /** Returns true when [address] is the device we're following. */
    @Synchronized
    fun offer(address: String, rssi: Int, now: Long, minRssi: Int): Boolean {
        val candidate = candidates.getOrPut(address) { Candidate(rssi.toDouble(), now) }
        candidate.rssi = candidate.rssi * 0.7 + rssi * 0.3
        candidate.lastSeen = now
        candidates.values.removeAll { now - it.lastSeen > windowMs }

        val eligible = candidates.filterValues { it.rssi >= minRssi }
        if (eligible.isEmpty()) return false

        val best = eligible.maxBy { it.value.rssi }
        val current = selected?.let { key -> eligible[key]?.let { key to it } }
        selected = when {
            current == null -> best.key
            best.value.rssi > current.second.rssi + switchMarginDb -> best.key
            else -> current.first
        }
        return address == selected
    }

    @Synchronized
    fun reset() {
        candidates.clear()
        selected = null
    }
}
