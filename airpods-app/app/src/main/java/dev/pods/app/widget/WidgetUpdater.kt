package dev.pods.app.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import dev.pods.app.PodsApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Coalesces widget refreshes; rendering a widget is far more expensive than a BLE packet. */
object WidgetUpdater {
    private const val MIN_INTERVAL_MS = 10_000L

    private var lastRun = 0L
    private var pending: Job? = null

    @Synchronized
    fun request(context: Context, immediate: Boolean = false) {
        val app = context.applicationContext
        if (pending?.isActive == true && !immediate) return
        pending?.cancel()
        val wait = if (immediate) 0L else (MIN_INTERVAL_MS - (System.currentTimeMillis() - lastRun)).coerceAtLeast(0L)
        pending = PodsApp.scope.launch {
            delay(wait)
            lastRun = System.currentTimeMillis()
            try {
                BatteryWidget().updateAll(app)
            } catch (e: Exception) {
                // No widgets placed, or the host is busy; the next change retries.
            }
        }
    }
}
