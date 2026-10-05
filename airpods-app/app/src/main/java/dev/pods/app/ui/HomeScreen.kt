package dev.pods.app.ui

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import android.widget.Toast
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.pods.app.BuildConfig
import dev.pods.app.bluetooth.PermissionSnapshot
import dev.pods.app.bluetooth.BtConnections
import dev.pods.app.bluetooth.ScanDiagnostics
import dev.pods.app.data.PodsState
import dev.pods.app.data.SettingsValues
import dev.pods.app.data.Signal
import dev.pods.app.ui.components.IconTile
import dev.pods.app.ui.components.PodsComponentsRow
import dev.pods.app.ui.components.Section
import dev.pods.app.ui.components.SettingsRow
import dev.pods.app.ui.components.StatusPill
import dev.pods.app.ui.components.TextButton
import dev.pods.app.ui.components.ToggleRow
import dev.pods.app.ui.components.wearSummary
import dev.pods.app.ui.theme.Pods
import dev.pods.app.ui.theme.PodsType
import dev.pods.app.ui.theme.SquircleShape
import kotlinx.coroutines.delay

/** Everything the home screen can ask the activity to do. */
interface HomeActions {
    fun updateSettings(transform: (SettingsValues) -> SettingsValues)
    fun requestOverlay()
    fun requestBatteryUnrestricted()
    fun openBluetoothSettings()
    fun addWidget()
    fun openLocationSettings() {}
}

@Composable
fun HomeScreen(
    state: PodsState,
    signal: Signal,
    settings: SettingsValues,
    permissions: PermissionSnapshot,
    actions: HomeActions,
    liveClock: Boolean = true,
    diagnostics: ScanDiagnostics = ScanDiagnostics(),
) {
    val colors = Pods.colors
    val listState = rememberLazyListState()
    val insets = WindowInsets.systemBars.asPaddingValues()
    val showBar by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 90 }
    }
    val now by produceNow(liveClock, signal.lastSeen)

    val nearby = !state.isConnected && signal.lastSeen > 0 && now - signal.lastSeen < 30_000
    val live = state.isConnected || nearby
    // AirPods only send readable battery while the case is open or now and then in use,
    // so between those moments we show the last reading and say how old it is.
    val fresh = signal.lastSeen > 0 && now - signal.lastSeen < 45_000
    val statusEncrypted = state.isConnected && !fresh && diagnostics.podsSeen - diagnostics.batterySeen > 100

    Box(Modifier.fillMaxSize().background(colors.background)) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = insets.calculateTopPadding() + 20.dp,
                bottom = insets.calculateBottomPadding() + 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item(key = "header") {
                Header(state, signal, nearby, now)
            }
            item(key = "hero") {
                Hero(state, live, fresh, signal.lastSeen, now)
            }
            item(key = "setup") {
                SetupCards(permissions, actions)
                if (state.isConnected && state.snapshot == null) {
                    WaitingCard(permissions, actions)
                }
            }
            item(key = "ear") {
                Section(header = "Ear Detection", modifier = Modifier.padding(top = 28.dp)) {
                    ToggleRow(
                        title = "Automatic Ear Detection",
                        subtitle = if (statusEncrypted) {
                            "Your AirPods encrypt in-ear status on Android, so this can't work right now"
                        } else {
                            "Pause when you take an AirPod out"
                        },
                        icon = Icons.Rounded.Hearing,
                        iconTint = colors.blue,
                        checked = settings.earDetection,
                        onCheckedChange = { on -> actions.updateSettings { it.copy(earDetection = on) } },
                    )
                    ToggleRow(
                        title = "Resume Playback",
                        subtitle = "Continue when you put it back in",
                        icon = Icons.Rounded.PlayArrow,
                        iconTint = colors.green,
                        checked = settings.autoResume,
                        enabled = settings.earDetection,
                        divider = false,
                        onCheckedChange = { on -> actions.updateSettings { it.copy(autoResume = on) } },
                    )
                }
            }
            item(key = "alerts") {
                val popupOn = settings.popup && permissions.overlay
                Section(
                    header = "Notifications",
                    footer = "Background Updates keeps the widget and notification current while your AirPods are connected.",
                    modifier = Modifier.padding(top = 28.dp),
                ) {
                    ToggleRow(
                        title = "Connection Pop-up",
                        subtitle = if (settings.popup && !permissions.overlay) "Needs “Display over other apps”" else null,
                        icon = Icons.Rounded.PictureInPicture,
                        iconTint = colors.indigo,
                        checked = popupOn,
                        onCheckedChange = { on ->
                            actions.updateSettings { it.copy(popup = on) }
                            if (on && !permissions.overlay) actions.requestOverlay()
                        },
                    )
                    ToggleRow(
                        title = "Low Battery Alerts",
                        icon = Icons.Rounded.BatteryAlert,
                        iconTint = colors.red,
                        checked = settings.lowBatteryAlert,
                        onCheckedChange = { on -> actions.updateSettings { it.copy(lowBatteryAlert = on) } },
                    )
                    ToggleRow(
                        title = "Background Updates",
                        icon = Icons.Rounded.Sync,
                        iconTint = colors.gray,
                        checked = settings.backgroundUpdates,
                        divider = false,
                        onCheckedChange = { on -> actions.updateSettings { it.copy(backgroundUpdates = on) } },
                    )
                }
            }
            item(key = "widget") {
                Section(
                    header = "Home Screen",
                    modifier = Modifier.padding(top = 28.dp),
                ) {
                    SettingsRow(
                        title = "Add Battery Widget",
                        icon = Icons.Rounded.Widgets,
                        iconTint = colors.teal,
                        chevron = true,
                        divider = false,
                        onClick = actions::addWidget,
                    )
                }
            }
            item(key = "about") {
                AboutSection(state, signal, now, actions)
            }
            item(key = "diagnostics") {
                DiagnosticsSection(diagnostics, state, permissions, now)
            }
            item(key = "footer") {
                Text(
                    text = "Pods ${BuildConfig.VERSION_NAME}\nAirPods report battery in 10% steps.",
                    style = PodsType.footnote,
                    color = colors.tertiaryLabel,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                )
            }
        }

        CollapsedTitleBar(title = state.deviceName, visible = showBar)
    }
}

@Composable
private fun produceNow(live: Boolean, fallback: Long) =
    androidx.compose.runtime.produceState(if (live) System.currentTimeMillis() else fallback, live) {
        while (live) {
            delay(5_000)
            value = System.currentTimeMillis()
        }
    }

@Composable
private fun Header(state: PodsState, signal: Signal, nearby: Boolean, now: Long) {
    val colors = Pods.colors
    val dotColor by animateColorAsState(
        when {
            state.isConnected -> colors.green
            nearby -> colors.blue
            else -> colors.gray
        },
        label = "dot",
    )
    val status = when {
        state.isConnected -> "Connected"
        nearby -> "Nearby"
        signal.lastSeen > 0 -> "Last seen " + DateUtils.getRelativeTimeSpanString(
            signal.lastSeen, now, DateUtils.MINUTE_IN_MILLIS,
        ).toString().replaceFirstChar { it.lowercase() }
        state.snapshot != null -> "Not connected"
        else -> "Searching for your AirPods…"
    }
    Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 18.dp)) {
        Text(
            text = state.deviceName,
            style = PodsType.largeTitle,
            color = colors.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(Modifier.width(7.dp))
            Text(status, style = PodsType.subhead, color = colors.secondaryLabel)
        }
    }
}

@Composable
private fun Hero(state: PodsState, live: Boolean, fresh: Boolean, lastSeen: Long, now: Long) {
    val colors = Pods.colors
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(SquircleShape(26.dp))
            .background(colors.card)
            .padding(top = 28.dp, bottom = 22.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PodsComponentsRow(snapshot = state.snapshot, live = live, showWear = fresh)
        Spacer(Modifier.height(18.dp))
        val summary = when {
            state.snapshot == null && state.isConnected && state.headsetBattery != null ->
                "Battery ${state.headsetBattery}% · reported by Android"
            state.isConnected && !fresh && state.headsetBattery != null ->
                "Now ${state.headsetBattery}% · reported by Android"
            state.snapshot != null && state.isConnected && !fresh && lastSeen > 0 ->
                "Updated " + DateUtils.getRelativeTimeSpanString(
                    lastSeen, now, DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString().replaceFirstChar { it.lowercase() } + " · open case to refresh"
            else -> wearSummary(state.snapshot, state.isConnected)
        }
        StatusPill(summary)
    }
}

@Composable
private fun SetupCards(permissions: PermissionSnapshot, actions: HomeActions) {
    val colors = Pods.colors
    Column {
        AnimatedVisibility(visible = !permissions.bluetoothEnabled, enter = fadeIn(), exit = fadeOut()) {
            SetupCard(
                icon = Icons.Rounded.BluetoothDisabled,
                tint = colors.blue,
                title = "Bluetooth is off",
                body = "Turn on Bluetooth to see your AirPods.",
                action = "Open Settings",
                onAction = actions::openBluetoothSettings,
            )
        }
        AnimatedVisibility(visible = !permissions.batteryUnrestricted, enter = fadeIn(), exit = fadeOut()) {
            SetupCard(
                icon = Icons.Rounded.BatteryChargingFull,
                tint = colors.orange,
                title = "Allow background activity",
                body = "Lets Pods start by itself when your AirPods connect, so the widget stays up to date.",
                action = "Allow",
                onAction = actions::requestBatteryUnrestricted,
            )
        }
    }
}

@Composable
private fun SetupCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit,
) {
    val colors = Pods.colors
    Row(
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .fillMaxWidth()
            .clip(SquircleShape(16.dp))
            .background(colors.card)
            .padding(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        IconTile(icon, tint, size = 34.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = PodsType.headline, color = colors.label)
            Spacer(Modifier.height(3.dp))
            Text(body, style = PodsType.subhead, color = colors.secondaryLabel)
            Spacer(Modifier.height(6.dp))
            TextButton(action, onAction, modifier = Modifier.offset(x = (-8).dp))
        }
    }
}

@Composable
private fun WaitingCard(permissions: PermissionSnapshot, actions: HomeActions) {
    val colors = Pods.colors
    if (!permissions.locationOn) {
        SetupCard(
            icon = Icons.Rounded.LocationOn,
            tint = colors.blue,
            title = "Waiting for battery info",
            body = "Some phones only pass Bluetooth scans to apps while Location is on. " +
                "Turn it on, then take an AirPod out or open the case next to your phone.",
            action = "Location Settings",
            onAction = actions::openLocationSettings,
        )
    } else {
        SetupCard(
            icon = Icons.Rounded.Bluetooth,
            tint = colors.blue,
            title = "Waiting for battery info",
            body = "Take an AirPod out or open the case next to your phone. " +
                "If nothing shows up, check Diagnostics at the bottom of this page.",
            action = "Bluetooth Settings",
            onAction = actions::openBluetoothSettings,
        )
    }
}

@Composable
private fun DiagnosticsSection(
    diagnostics: ScanDiagnostics,
    state: PodsState,
    permissions: PermissionSnapshot,
    now: Long,
) {
    val colors = Pods.colors
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    fun ago(at: Long): String {
        val seconds = ((now - at) / 1000).coerceAtLeast(0)
        return when {
            seconds < 5 -> "now"
            seconds < 120 -> "${seconds}s ago"
            else -> "${seconds / 60}m ago"
        }
    }
    val lastSignal = if (diagnostics.lastPacketAt == 0L) {
        "None yet"
    } else {
        "${diagnostics.lastRssi ?: "?"} dBm · ${ago(diagnostics.lastPacketAt)}"
    }
    val systemBattery = state.headsetBattery?.let { "$it%" } ?: BtConnections.systemBatteryStatus
    Section(
        header = "Diagnostics",
        footer = if (expanded) {
            "Messages starting 07 19 01 carry readable battery. Other AirPods messages are encrypted."
        } else {
            null
        },
        modifier = Modifier.padding(top = 28.dp),
    ) {
        SettingsRow(
            title = "Scanner",
            value = diagnostics.status,
            icon = Icons.Rounded.BugReport,
            iconTint = colors.gray,
            divider = expanded,
            chevron = !expanded,
            onClick = { expanded = !expanded },
        )
        if (expanded) {
            SettingsRow(title = "Scan mode", value = diagnostics.strategy.label)
            SettingsRow(title = "Apple signals", value = diagnostics.appleSeen.toString())
            SettingsRow(title = "AirPods signals", value = diagnostics.podsSeen.toString())
            SettingsRow(title = "Battery packets", value = diagnostics.batterySeen.toString())
            SettingsRow(title = "Last battery packet", value = lastSignal)
            SettingsRow(title = "Battery from Android", value = systemBattery)
            SettingsRow(title = "Location", value = if (permissions.locationOn) "On" else "Off")
            SettingsRow(
                title = "Extended advertising",
                value = when (diagnostics.extendedAdvertising) {
                    true -> "Supported"
                    false -> "Not supported"
                    null -> "—"
                },
            )
            diagnostics.packetKinds.forEach { kind ->
                SettingsRow(
                    title = kind.prefix,
                    value = "×${kind.count} · ${kind.lastRssi} dBm · ${ago(kind.lastAt)}",
                )
            }
            SettingsRow(
                title = "Copy Diagnostics",
                titleColor = colors.blue,
                divider = false,
                onClick = {
                    clipboard.setText(AnnotatedString(diagnosticsReport(diagnostics, state, permissions, now)))
                    Toast.makeText(context, "Diagnostics copied", Toast.LENGTH_SHORT).show()
                },
            )
        }
    }
}

private fun diagnosticsReport(
    d: ScanDiagnostics,
    state: PodsState,
    permissions: PermissionSnapshot,
    now: Long,
): String = buildString {
    appendLine("Pods ${BuildConfig.VERSION_NAME} diagnostics")
    appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}, Android ${android.os.Build.VERSION.RELEASE}")
    appendLine("Connected: ${state.connected?.name ?: "no"}")
    appendLine("Scanner: ${d.status}, mode ${d.strategy.label}")
    appendLine("Apple ${d.appleSeen}, AirPods ${d.podsSeen}, battery packets ${d.batterySeen}")
    appendLine("System battery: ${state.headsetBattery?.let { "$it%" } ?: BtConnections.systemBatteryStatus}")
    appendLine(
        "Location ${permissions.locationOn}, offload filter ${d.offloadedFiltering}, " +
            "batching ${d.offloadedBatching}, extended adv ${d.extendedAdvertising}"
    )
    state.snapshot?.let {
        appendLine("Snapshot: model 0x%04X L ${it.left} R ${it.right} C ${it.case}, %ds old".format(it.modelId, (now - it.updatedAt) / 1000))
    }
    appendLine("Packets:")
    d.packetKinds.forEach {
        appendLine("${it.prefix} x${it.count} ${it.lastRssi}dBm ${(now - it.lastAt) / 1000}s ago")
        appendLine("  ${it.lastHex}")
    }
}

@Composable
private fun AboutSection(state: PodsState, signal: Signal, now: Long, actions: HomeActions) {
    val colors = Pods.colors
    val signalText = signal.rssi?.takeIf { now - signal.lastSeen < 60_000 }?.let { rssi ->
        val quality = when {
            rssi >= -55 -> "Excellent"
            rssi >= -67 -> "Good"
            rssi >= -80 -> "Fair"
            else -> "Weak"
        }
        "$quality · $rssi dBm"
    } ?: "—"
    val updated = when {
        signal.lastSeen == 0L -> "Never"
        now - signal.lastSeen < 15_000 -> "Just now"
        else -> DateUtils.getRelativeTimeSpanString(signal.lastSeen, now, DateUtils.MINUTE_IN_MILLIS).toString()
    }
    Section(header = "About", modifier = Modifier.padding(top = 28.dp)) {
        SettingsRow(title = "Model", value = state.modelName, icon = Icons.Rounded.Info, iconTint = colors.gray)
        SettingsRow(title = "Signal", value = signalText, icon = Icons.Rounded.Bluetooth, iconTint = colors.blue)
        SettingsRow(title = "Updated", value = updated, icon = Icons.Rounded.Sync, iconTint = colors.green)
        SettingsRow(
            title = "Bluetooth Settings",
            icon = Icons.Rounded.Bluetooth,
            iconTint = colors.indigo,
            chevron = true,
            divider = false,
            onClick = actions::openBluetoothSettings,
        )
    }
}

@Composable
private fun CollapsedTitleBar(title: String, visible: Boolean) {
    val colors = Pods.colors
    val alpha by animateFloatAsState(if (visible) 1f else 0f, label = "bar")
    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { this.alpha = alpha }
            .background(colors.background.copy(alpha = 0.94f))
            .statusBarsPadding()
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title,
                style = PodsType.headline,
                color = colors.label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 48.dp),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.separator)
        )
    }
}
