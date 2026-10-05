package dev.pods.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.pods.app.ble.PodKind
import dev.pods.app.ui.components.BudIllustration
import dev.pods.app.ui.components.CaseIllustration
import dev.pods.app.ui.components.PrimaryButton
import dev.pods.app.ui.theme.Pods
import dev.pods.app.ui.theme.PodsType

/** Apple "Welcome" sheet: what the app does, then one button to grant access. */
@Composable
fun OnboardingScreen(deniedBefore: Boolean, onContinue: () -> Unit) {
    val colors = Pods.colors
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
            .systemBarsPadding()
            .padding(horizontal = 28.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BudIllustration(left = true, kind = PodKind.PRO, modifier = Modifier.size(width = 54.dp, height = 88.dp))
                BudIllustration(left = false, kind = PodKind.PRO, modifier = Modifier.size(width = 54.dp, height = 88.dp))
                Spacer(Modifier.width(10.dp))
                CaseIllustration(kind = PodKind.PRO, lightColor = colors.green, modifier = Modifier.size(width = 100.dp, height = 78.dp))
            }
            Spacer(Modifier.height(36.dp))
            Text(
                "Welcome to Pods",
                style = PodsType.largeTitle,
                color = colors.label,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Your AirPods, at home on Android.",
                style = PodsType.body,
                color = colors.secondaryLabel,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(40.dp))
            Feature(
                icon = Icons.Rounded.BatteryChargingFull,
                tint = colors.green,
                title = "Battery at a glance",
                body = "See each AirPod and the case the moment you open it.",
            )
            Feature(
                icon = Icons.Rounded.Hearing,
                tint = colors.blue,
                title = "Ear detection",
                body = "Music pauses when you take an AirPod out, and picks up when you put it back.",
            )
            Feature(
                icon = Icons.Rounded.Widgets,
                tint = colors.teal,
                title = "Home Screen widget",
                body = "Battery rings for your AirPods, case and phone.",
            )
        }
        Text(
            text = if (deniedBefore) {
                "Pods needs Nearby devices access. Open Settings, tap Permissions and allow it."
            } else {
                "Pods reads the battery your AirPods broadcast over Bluetooth. Nothing leaves your phone."
            },
            style = PodsType.footnote,
            color = colors.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp, top = 12.dp),
        )
        PrimaryButton(
            text = if (deniedBefore) "Open Settings" else "Continue",
            onClick = onContinue,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, body: String) {
    val colors = Pods.colors
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = PodsType.headline, color = colors.label)
            Spacer(Modifier.height(2.dp))
            Text(body, style = PodsType.subhead, color = colors.secondaryLabel)
        }
    }
}
