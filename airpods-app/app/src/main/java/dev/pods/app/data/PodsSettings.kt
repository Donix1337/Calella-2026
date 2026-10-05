package dev.pods.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SettingsValues(
    val earDetection: Boolean = true,
    val autoResume: Boolean = true,
    val popup: Boolean = true,
    val lowBatteryAlert: Boolean = true,
    val backgroundUpdates: Boolean = true,
    val onboarded: Boolean = false,
)

object PodsSettings {
    private lateinit var prefs: SharedPreferences
    private val _values = MutableStateFlow(SettingsValues())
    val values: StateFlow<SettingsValues> = _values.asStateFlow()
    val current: SettingsValues get() = _values.value

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            prefs = context.applicationContext.getSharedPreferences("pods_settings", Context.MODE_PRIVATE)
            val d = SettingsValues()
            _values.value = SettingsValues(
                earDetection = prefs.getBoolean("earDetection", d.earDetection),
                autoResume = prefs.getBoolean("autoResume", d.autoResume),
                popup = prefs.getBoolean("popup", d.popup),
                lowBatteryAlert = prefs.getBoolean("lowBatteryAlert", d.lowBatteryAlert),
                backgroundUpdates = prefs.getBoolean("backgroundUpdates", d.backgroundUpdates),
                onboarded = prefs.getBoolean("onboarded", d.onboarded),
            )
            initialized = true
        }
    }

    fun update(transform: (SettingsValues) -> SettingsValues) {
        val v = transform(_values.value)
        _values.value = v
        if (!initialized) return
        prefs.edit()
            .putBoolean("earDetection", v.earDetection)
            .putBoolean("autoResume", v.autoResume)
            .putBoolean("popup", v.popup)
            .putBoolean("lowBatteryAlert", v.lowBatteryAlert)
            .putBoolean("backgroundUpdates", v.backgroundUpdates)
            .putBoolean("onboarded", v.onboarded)
            .apply()
    }
}
