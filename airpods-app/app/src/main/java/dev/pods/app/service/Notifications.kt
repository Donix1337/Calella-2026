package dev.pods.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.pods.app.R
import dev.pods.app.data.PodsRepository
import dev.pods.app.data.PodsState
import dev.pods.app.ui.MainActivity

object Notifications {
    const val CHANNEL_STATUS = "status"
    const val CHANNEL_ALERTS = "alerts"
    const val STATUS_ID = 1
    const val LOW_BATTERY_ID = 2

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_STATUS, "AirPods status", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Live battery levels while your AirPods are connected"
                    setShowBadge(false)
                },
                NotificationChannel(CHANNEL_ALERTS, "Battery alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Tells you when your AirPods are running low"
                },
            )
        )
    }

    fun statusText(state: PodsState): String {
        val s = state.snapshot ?: return state.headsetBattery?.let { "Battery $it%" } ?: "Connected"
        val system = state.headsetBattery
        if (system != null && !PodsRepository.isLive() && !s.isHeadphones) {
            val case = s.case?.let { "   ·   Case $it%" } ?: ""
            return "AirPods $system%$case"
        }
        fun part(label: String, level: Int?, charging: Boolean) =
            level?.let { "$label $it%" + if (charging) " ⚡" else "" }
        val parts = if (s.isHeadphones) {
            listOfNotNull(part("Battery", s.single, s.singleCharging))
        } else {
            listOfNotNull(
                part("L", s.left, s.leftCharging),
                part("R", s.right, s.rightCharging),
                if (s.caseFromMemory) null else part("Case", s.case, s.caseCharging),
            )
        }
        return if (parts.isEmpty()) "Connected" else parts.joinToString("   ·   ")
    }

    fun status(context: Context, state: PodsState): Notification =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_pods)
            .setContentTitle(state.deviceName)
            .setContentText(statusText(state))
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    fun notifyStatus(context: Context, state: PodsState) {
        post(context, STATUS_ID, status(context, state))
    }

    fun notifyLowBattery(context: Context, title: String, text: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_pods)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        post(context, LOW_BATTERY_ID, notification)
    }

    private fun post(context: Context, id: Int, notification: Notification) {
        try {
            context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
        } catch (e: SecurityException) {
            // Notifications permission not granted; nothing to show.
        }
    }

    fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
