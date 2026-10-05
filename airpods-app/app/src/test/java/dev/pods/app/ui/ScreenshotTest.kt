package dev.pods.app.ui

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.takahirom.roborazzi.captureRoboImage
import dev.pods.app.bluetooth.PermissionSnapshot
import dev.pods.app.data.DeviceInfo
import dev.pods.app.data.PhoneBattery
import dev.pods.app.data.PodsSnapshot
import dev.pods.app.data.PodsState
import dev.pods.app.data.SettingsValues
import dev.pods.app.data.Signal
import dev.pods.app.popup.ConnectionPopup
import dev.pods.app.ui.theme.Pods
import dev.pods.app.ui.theme.PodsTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders the main screens to PNGs for design review.
 * Run with: ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' -PrecordScreens
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w393dp-h852dp-xxhdpi")
class ScreenshotTest {

    private val now = 1_760_000_000_000L

    private val snapshot = PodsSnapshot(
        modelId = 0x2014,
        left = 80, right = 70, case = 60,
        leftCharging = false, rightCharging = false, caseCharging = true,
        leftInEar = true, rightInEar = true,
        leftInCase = false, rightInCase = false,
        single = null, singleCharging = false,
        updatedAt = now,
    )

    private val connected = PodsState(
        snapshot = snapshot,
        connected = DeviceInfo("Nik’s AirPods Pro", "00:11:22:33:44:55"),
        phone = PhoneBattery(74, false),
    )

    private val permissions = PermissionSnapshot(
        bluetooth = true, notifications = true, overlay = true,
        batteryUnrestricted = true, bluetoothEnabled = true,
    )

    private val actions = object : HomeActions {
        override fun updateSettings(transform: (SettingsValues) -> SettingsValues) = Unit
        override fun requestOverlay() = Unit
        override fun requestBatteryUnrestricted() = Unit
        override fun openBluetoothSettings() = Unit
        override fun addWidget() = Unit
    }

    private fun shoot(name: String, dark: Boolean, content: @Composable () -> Unit) {
        RuntimeEnvironment.setQualifiers(if (dark) "+night" else "+notnight")
        captureRoboImage("build/screens/$name.png") {
            PodsTheme { content() }
        }
    }

    @Test
    fun homeLight() = shoot("home_light", dark = false) {
        HomeScreen(connected, Signal(-58, now), SettingsValues(onboarded = true), permissions, actions, liveClock = false)
    }

    @Test
    fun homeDark() = shoot("home_dark", dark = true) {
        HomeScreen(connected, Signal(-58, now), SettingsValues(onboarded = true), permissions, actions, liveClock = false)
    }

    @Test
    fun homeSetupNeeded() = shoot("home_setup", dark = false) {
        HomeScreen(
            PodsState(),
            Signal(),
            SettingsValues(),
            permissions.copy(batteryUnrestricted = false, overlay = false),
            actions,
            liveClock = false,
        )
    }

    @Test
    fun onboarding() = shoot("onboarding", dark = false) {
        OnboardingScreen(deniedBefore = false, onContinue = {})
    }

    @Test
    fun popupLight() = shoot("popup_light", dark = false) {
        PopupPreview(connected)
    }

    @Test
    fun popupDark() = shoot("popup_dark", dark = true) {
        PopupPreview(connected.copy(snapshot = snapshot.copy(leftInEar = false, rightInEar = false, leftInCase = true, rightInCase = true, leftCharging = true, rightCharging = true)))
    }

    @Composable
    private fun PopupPreview(state: PodsState) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (Pods.colors.isDark) androidx.compose.ui.graphics.Color(0xFF203040) else androidx.compose.ui.graphics.Color(0xFFB8C6D9)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            ConnectionPopup(state, MutableTransitionState(true), onDismiss = {}, onOpen = {})
        }
    }
}
