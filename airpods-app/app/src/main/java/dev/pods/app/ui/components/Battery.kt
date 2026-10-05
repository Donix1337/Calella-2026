package dev.pods.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.pods.app.ble.PodKind
import dev.pods.app.data.PodsSnapshot
import dev.pods.app.ui.theme.Pods
import dev.pods.app.ui.theme.PodsType
import kotlin.math.max

/** iOS status-bar style battery: outlined body, nub, proportional fill. */
@Composable
fun BatteryGlyph(level: Int?, charging: Boolean, modifier: Modifier = Modifier.size(width = 25.dp, height = 12.dp)) {
    val colors = Pods.colors
    val fraction by animateFloatAsState((level ?: 0) / 100f, tween(700), label = "battery")
    val fill = when {
        level == null -> Color.Transparent
        charging -> colors.green
        level <= 10 -> colors.red
        level <= 20 -> colors.orange
        else -> colors.label
    }
    Canvas(modifier) {
        val stroke = 1.dp.toPx()
        val nubWidth = size.width * 0.075f
        val gap = 1.dp.toPx()
        val bodyWidth = size.width - nubWidth - gap
        val radius = size.height * 0.32f
        drawRoundRect(
            color = colors.label.copy(alpha = 0.35f),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size(bodyWidth - stroke, size.height - stroke),
            cornerRadius = CornerRadius(radius),
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = colors.label.copy(alpha = 0.4f),
            topLeft = Offset(bodyWidth + gap, size.height * 0.33f),
            size = Size(nubWidth, size.height * 0.34f),
            cornerRadius = CornerRadius(nubWidth),
        )
        val inset = stroke + 1.5.dp.toPx()
        val full = bodyWidth - inset * 2f
        if (level != null && fraction > 0f) {
            drawRoundRect(
                color = fill,
                topLeft = Offset(inset, inset),
                size = Size(max(full * fraction, size.height * 0.25f), size.height - inset * 2f),
                cornerRadius = CornerRadius(radius * 0.55f),
            )
        }
    }
}

/** "⚡ ▭ 80%" under each component. */
@Composable
fun BatteryReading(level: Int?, charging: Boolean, dimmed: Boolean = false) {
    val colors = Pods.colors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.alpha(if (dimmed) 0.5f else 1f)) {
        if (charging && level != null) {
            Icon(Icons.Rounded.Bolt, contentDescription = "Charging", tint = colors.green, modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(1.dp))
        }
        BatteryGlyph(level, charging)
        Spacer(Modifier.width(6.dp))
        Text(
            text = level?.let { "$it%" } ?: "—",
            style = PodsType.number,
            color = if (level == null) colors.tertiaryLabel else colors.label,
        )
    }
}

@Composable
private fun ComponentColumn(
    label: String,
    detail: String?,
    level: Int?,
    charging: Boolean,
    dimmed: Boolean,
    artHeight: Dp,
    modifier: Modifier = Modifier,
    art: @Composable () -> Unit,
) {
    val colors = Pods.colors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.height(artHeight), contentAlignment = Alignment.BottomCenter) { art() }
        Spacer(Modifier.height(14.dp))
        BatteryReading(level, charging, dimmed)
        Spacer(Modifier.height(3.dp))
        Text(label, style = PodsType.footnote, color = colors.secondaryLabel)
        Text(
            text = detail ?: " ",
            style = PodsType.caption,
            color = colors.tertiaryLabel,
        )
    }
}

/**
 * Left bud, right bud and case side by side with their battery levels, like
 * the card iPhone shows when you open the case.
 */
@Composable
fun PodsComponentsRow(
    snapshot: PodsSnapshot?,
    live: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showWear: Boolean = true,
) {
    val colors = Pods.colors
    val kind = snapshot?.kind ?: PodKind.PRO
    val artHeight = if (compact) 84.dp else 100.dp
    val dim = Modifier.alpha(if (snapshot == null) 0.35f else 1f)

    if (snapshot?.isHeadphones == true) {
        Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Headphones,
                contentDescription = null,
                tint = colors.secondaryLabel,
                modifier = Modifier.size(artHeight),
            )
            Spacer(Modifier.height(12.dp))
            BatteryReading(snapshot.single, snapshot.singleCharging, dimmed = !live)
        }
        return
    }

    fun wearDetail(inEar: Boolean, inCase: Boolean): String? = when {
        snapshot == null || !live || !showWear -> null
        inEar -> "In ear"
        inCase -> "In case"
        else -> null
    }

    val caseLight = when {
        snapshot == null || !live || snapshot.caseFromMemory -> null
        snapshot.caseCharging -> colors.green
        (snapshot.case ?: 100) <= 20 -> colors.orange
        snapshot.leftInCase || snapshot.rightInCase -> colors.green.copy(alpha = 0.85f)
        else -> null
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            ComponentColumn(
                label = "Left",
                detail = wearDetail(snapshot?.leftInEar == true, snapshot?.leftInCase == true),
                level = snapshot?.left,
                charging = snapshot?.leftCharging == true,
                dimmed = !live,
                artHeight = artHeight,
            ) {
                BudIllustration(left = true, kind = kind, modifier = dim.size(width = artHeight * 0.6f, height = artHeight))
            }
            ComponentColumn(
                label = "Right",
                detail = wearDetail(snapshot?.rightInEar == true, snapshot?.rightInCase == true),
                level = snapshot?.right,
                charging = snapshot?.rightCharging == true,
                dimmed = !live,
                artHeight = artHeight,
            ) {
                BudIllustration(left = false, kind = kind, modifier = dim.size(width = artHeight * 0.6f, height = artHeight))
            }
        }
        ComponentColumn(
            label = "Case",
            detail = if (snapshot?.caseFromMemory == true && snapshot.case != null) "Last known" else null,
            level = snapshot?.case,
            charging = snapshot?.caseCharging == true,
            dimmed = !live || snapshot?.caseFromMemory == true,
            artHeight = artHeight,
        ) {
            CaseIllustration(
                kind = kind,
                lightColor = caseLight,
                modifier = dim.size(width = artHeight * 1.18f, height = artHeight * 0.86f),
            )
        }
    }
}

fun wearSummary(snapshot: PodsSnapshot?, connected: Boolean): String = when {
    snapshot == null -> "Open the case near your phone"
    snapshot.isHeadphones -> if (connected) "Connected" else "Not connected"
    !connected -> "Not connected"
    snapshot.leftInEar && snapshot.rightInEar -> "Both AirPods in ear"
    snapshot.leftInEar -> "Left AirPod in ear"
    snapshot.rightInEar -> "Right AirPod in ear"
    snapshot.leftInCase && snapshot.rightInCase -> "Both AirPods in case"
    else -> "Not in ear"
}

@Composable
fun StatusPill(text: String, modifier: Modifier = Modifier) {
    val colors = Pods.colors
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(colors.fill)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Hearing, contentDescription = null, tint = colors.secondaryLabel, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = PodsType.footnote, color = colors.secondaryLabel)
    }
}
