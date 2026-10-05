package dev.pods.app.service

import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import dev.pods.app.data.PodsSnapshot
import dev.pods.app.data.SettingsValues

/**
 * Apple-style automatic ear detection: pause when an AirPod comes out while
 * something is playing, and resume when it goes back in.
 */
class EarDetector(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    private var stableCount: Int? = null
    private var candidateCount: Int? = null
    private var candidateSince = 0L

    private var pausedByUs = false
    private var pausedAt = 0L
    private var countBeforePause = 0

    fun onSnapshot(snapshot: PodsSnapshot, settings: SettingsValues, now: Long) {
        if (snapshot.isHeadphones) return
        val count = snapshot.inEarCount

        // Ignore one-off glitches: a new count has to hold for a moment.
        if (candidateCount != count) {
            candidateCount = count
            candidateSince = now
            return
        }
        if (now - candidateSince < DEBOUNCE_MS) return

        val previous = stableCount
        if (previous == count) return
        stableCount = count
        if (previous == null) return

        if (snapshot.leftInCase && snapshot.rightInCase) pausedByUs = false

        if (count < previous) {
            if (settings.earDetection && audio?.isMusicActive == true) {
                sendKey(KeyEvent.KEYCODE_MEDIA_PAUSE)
                pausedByUs = true
                pausedAt = now
                countBeforePause = previous
            }
        } else if (pausedByUs && count >= countBeforePause) {
            pausedByUs = false
            val recent = now - pausedAt < RESUME_WINDOW_MS
            if (settings.earDetection && settings.autoResume && recent && audio?.isMusicActive != true) {
                sendKey(KeyEvent.KEYCODE_MEDIA_PLAY)
            }
        }
    }

    /** Call after a check that didn't produce a snapshot for a while (e.g. reconnect). */
    fun reset() {
        stableCount = null
        candidateCount = null
        pausedByUs = false
    }

    /** Re-run the debounce timer when advertisements are sparse. */
    fun tick(snapshot: PodsSnapshot?, settings: SettingsValues, now: Long) {
        if (snapshot != null && candidateCount == snapshot.inEarCount) onSnapshot(snapshot, settings, now)
    }

    private fun sendKey(code: Int) {
        val am = audio ?: return
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    private companion object {
        const val DEBOUNCE_MS = 700L
        const val RESUME_WINDOW_MS = 3 * 60_000L
    }
}
